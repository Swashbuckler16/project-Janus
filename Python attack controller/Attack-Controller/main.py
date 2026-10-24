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
    found_pin = None
    total_response_time = 0

    try:

        for pin in generate_pins(config.start, config.end):

            if total_attempts >= config.max_attempts:
                print("Maximum attempt limit reached.")
                break

            print(f"Testing PIN: {pin}")

            result = test_pin(
                pin,
                config.ip,
                config.port
            )

            if result is None:
                print("Connection failed.")
                break

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
                pin,
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

                found_pin = pin

                print()
                print(f"SUCCESS → PIN {pin}")
                print()

                break

            if config.delay > 0:
                time.sleep(config.delay)

    finally:

        log_file.close()

    elapsed = time.perf_counter() - experiment_start

    print()
    print("=" * 45)
    print("EXPERIMENT SUMMARY")
    print("=" * 45)

    print(f"Attempts          : {total_attempts}")
    print(f"Elapsed time      : {elapsed:.2f} seconds")

    if elapsed > 0:
        rate = total_attempts / elapsed
        print(f"Requests/second   : {rate:.2f}")

    if total_attempts > 0:
        average = total_response_time / total_attempts
        print(f"Avg response time : {average:.2f} ms")

    if found_pin:
        print(f"PIN discovered    : {found_pin}")
    else:
        print("PIN discovered    : No")

    print()
    print(f"Log file          : {filename}")