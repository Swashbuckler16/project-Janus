import json
import time
import argparse
from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError
from pin_generator import generate_pins
from logger import create_log_file


TARGET_IP = "192.168.1.45"
TARGET_PORT = 8080


def test_pin(pin: str, target_ip: str, target_port: int):
    url = f"http://{target_ip}:{target_port}/lab/verify"

    payload = json.dumps({
        "pin": pin
    }).encode("utf-8")

    request = Request(
        url,
        data=payload,
        headers={
            "Content-Type": "application/json"
        },
        method="POST"
    )

    start = time.perf_counter()

    try:
        with urlopen(request, timeout=5) as response:
            result = json.loads(response.read().decode("utf-8"))

        elapsed_ms = (time.perf_counter() - start) * 1000

        return {
            "success": result.get("success", False),
            "attempt": result.get("attempt"),
            "response_time_ms": result.get("responseTimeMs"),
            "execution_time_nanos": result.get("executionTimeNanos"),
            "algorithm": result.get("algorithm", "UNKNOWN"),
            "constant_time": result.get("constantTime", False),
            "hardware_backed": result.get("hardwareBacked", False),
            "network_time_ms": round(elapsed_ms, 2)
        }

    except HTTPError as e:
        elapsed_ms = (time.perf_counter() - start) * 1000
        if e.code == 429:
            try:
                error_body = json.loads(e.read().decode("utf-8"))
            except Exception:
                error_body = {}
            retry_header = e.headers.get("Retry-After")
            retry_after = int(retry_header) if retry_header and retry_header.isdigit() else int(error_body.get("retryAfterSeconds") or 1)
            return {
                "success": False,
                "rate_limited": True,
                "tier": error_body.get("tier", "RATE_LIMIT"),
                "failed_attempts": error_body.get("failedAttempts"),
                "retry_after": retry_after,
                "reason": error_body.get("reason", "Defensive rate limit triggered"),
                "attempt": None,
                "response_time_ms": round(elapsed_ms, 2),
                "execution_time_nanos": None,
                "algorithm": None,
                "constant_time": None,
                "hardware_backed": None,
                "network_time_ms": round(elapsed_ms, 2)
            }
        print(f"HTTP error: {e.code}")
        return None

    except URLError as e:
        print(f"Connection error: {e.reason}")
        return None


def get_config():
    parser = argparse.ArgumentParser(
        description="Project Janus - Controlled authentication security research testbed"
    )

    parser.add_argument(
        "--ip",
        default=TARGET_IP,
        help="Target Android device IP address"
    )

    parser.add_argument(
        "--port",
        type=int,
        default=TARGET_PORT,
        help="Target server port"
    )

    parser.add_argument(
        "--start",
        type=int,
        default=0,
        help="Starting PIN number"
    )

    parser.add_argument(
        "--end",
        type=int,
        default=9,
        help="Ending PIN number"
    )

    parser.add_argument(
        "--delay",
        type=float,
        default=0.0,
        help="Delay between requests in seconds"
    )

    parser.add_argument(
        "--max-attempts",
        type=int,
        default=100,
        help="Maximum number of authentication attempts"
    )

    parser.add_argument(
        "--wait-on-rate-limit",
        action="store_true",
        help="If rate limited (HTTP 429), sleep for the cooldown duration and retry"
    )

    parser.add_argument(
        "--pdf",
        action="store_true",
        help="Automatically generate a detailed forensic PDF audit report upon experiment completion"
    )

    return parser.parse_args()


if __name__ == "__main__":

    config = get_config()

    if not 0 <= config.start <= 9999:
        raise ValueError("Start PIN must be between 0000 and 9999")

    if not 0 <= config.end <= 9999:
        raise ValueError("End PIN must be between 0000 and 9999")

    if config.start > config.end:
        raise ValueError("Start PIN cannot be greater than end PIN")

    if config.max_attempts <= 0:
        raise ValueError("Maximum attempts must be greater than zero")

    print()
    print("PROJECT JANUS - SECURITY RESEARCH HARNESS")
    print("=" * 45)

    print(f"Target IP        : {config.ip}")
    print(f"Target port      : {config.port}")
    print(f"PIN range        : {config.start:04d} - {config.end:04d}")
    print(f"Request delay    : {config.delay:.3f} sec")
    print(f"Maximum attempts : {config.max_attempts}")
    print()

    log_file, writer, filename = create_log_file()

    experiment_start = time.perf_counter()

    total_attempts = 0
    rate_limit_events = 0
    found_pin = None
    total_response_time = 0

    try:

        pin_iterator = generate_pins(config.start, config.end)
        current_pin = next(pin_iterator, None)

        while current_pin is not None:

            if total_attempts >= config.max_attempts:
                print("Maximum attempt limit reached.")
                break

            print(f"Testing PIN: {current_pin}")

            result = test_pin(
                current_pin,
                config.ip,
                config.port
            )

            if result is None:
                print("Connection failed.")
                break

            # Handle HTTP 429 Rate Limiting & Lockout
            if result.get("rate_limited"):
                rate_limit_events += 1
                tier = result.get("tier", "RATE_LIMIT")
                retry_after = result.get("retry_after", 1)
                reason = result.get("reason", "Throttled")

                print(f"  [!] HTTP 429 DEFENSE INTERVENTION: Level: {tier} | Cooldown: {retry_after}s | {reason}")

                writer.writerow([
                    time.strftime("%Y-%m-%d %H:%M:%S"),
                    current_pin,
                    "RATE_LIMITED_429",
                    result.get("failed_attempts"),
                    result["response_time_ms"],
                    result["network_time_ms"],
                    tier,
                    "N/A",
                    "N/A"
                ])
                log_file.flush()

                if config.wait_on_rate_limit:
                    print(f"  [*] Waiting {retry_after}s for defense cooldown before retry...")
                    time.sleep(retry_after)
                    # Retry the same PIN
                    continue
                else:
                    print("  [*] --wait-on-rate-limit not set. Continuing to next PIN candidate.\n")

            else:
                if total_attempts == 0:
                    print(f"[*] Target Profile:")
                    print(f"    - Algorithm       : {result.get('algorithm')}")
                    print(f"    - Constant Time   : {result.get('constant_time')}")
                    print(f"    - Hardware-Backed : {result.get('hardware_backed')}\n")

                total_attempts += 1
                response_time = result["response_time_ms"]
                if response_time is not None:
                    total_response_time += response_time

                writer.writerow([
                    time.strftime("%Y-%m-%d %H:%M:%S"),
                    current_pin,
                    result["success"],
                    result["attempt"],
                    result["response_time_ms"],
                    result["network_time_ms"],
                    result.get("algorithm"),
                    result.get("constant_time"),
                    result.get("hardware_backed")
                ])
                log_file.flush()

                if result["success"]:
                    found_pin = current_pin
                    print()
                    print(f"SUCCESS → PIN {current_pin}")
                    print()
                    break

            if config.delay > 0:
                time.sleep(config.delay)

            current_pin = next(pin_iterator, None)

    finally:

        log_file.close()

    elapsed = time.perf_counter() - experiment_start

    print()
    print("=" * 45)
    print("EXPERIMENT SUMMARY")
    print("=" * 45)

    print(f"Attempts Completed    : {total_attempts}")
    print(f"Defense Interventions : {rate_limit_events} (HTTP 429)")
    print(f"Elapsed time          : {elapsed:.2f} seconds")

    if elapsed > 0:
        rate = total_attempts / elapsed
        print(f"Requests/second       : {rate:.2f}")

    if total_attempts > 0:
        average = total_response_time / total_attempts
        print(f"Avg response time     : {average:.2f} ms")

    if found_pin:
        print(f"PIN discovered        : {found_pin}")
    else:
        print("PIN discovered        : No")

    print()
    print(f"Log file          : {filename}")

    if config.pdf:
        try:
            from generate_attack_report import parse_csv_run, create_pdf_report
            from pathlib import Path
            csv_path = Path(filename)
            out_pdf = csv_path.with_name(f"{csv_path.stem}_audit_report.pdf")
            print(f"\n[*] Generating forensic audit PDF report...")
            data = parse_csv_run(csv_path)
            if data:
                create_pdf_report(data, out_pdf)
                print(f"[+] Audit PDF Report saved: {out_pdf}")
        except Exception as e:
            print(f"[-] Warning: Failed to generate PDF report: {e}")