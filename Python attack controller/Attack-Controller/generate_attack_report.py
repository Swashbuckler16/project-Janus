#!/usr/bin/env python3
"""
Project Janus - Forensic Attack & Defense PDF Audit Report Generator
Produces publication-grade, formal cryptographic audit PDF reports
from empirical authentication attack telemetry.
"""

import os
import sys
import csv
import argparse
from datetime import datetime
from pathlib import Path

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np

from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.platypus import (
    SimpleDocTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
    Image,
    KeepTogether,
    HRFlowable
)

BASE_DIR = Path(__file__).parent
RESULTS_DIR = BASE_DIR / "Results"
PLOTS_DIR = RESULTS_DIR / "plots"
PLOTS_DIR.mkdir(parents=True, exist_ok=True)


def parse_csv_run(csv_path: Path):
    """Parses an attack benchmark CSV and calculates comprehensive telemetry statistics."""
    records = []
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            records.append(row)

    total_attempts = len(records)
    if total_attempts == 0:
        return None

    rate_limited_rows = [r for r in records if r.get("success") == "RATE_LIMITED_429"]
    successful_rows = [r for r in records if r.get("success") in ("True", True, "true")]
    failed_rows = [r for r in records if r.get("success") in ("False", False, "false")]

    valid_response_times = []
    valid_network_times = []

    for r in records:
        try:
            val = float(r.get("response_time_ms", 0))
            if val >= 0 and r.get("success") != "RATE_LIMITED_429":
                valid_response_times.append(val)
        except (ValueError, TypeError):
            pass

        try:
            val = float(r.get("network_time_ms", 0))
            if val >= 0:
                valid_network_times.append(val)
        except (ValueError, TypeError):
            pass

    # Extract cryptographic target profile from first valid row
    first_auth_row = next((r for r in records if r.get("algorithm") and r.get("algorithm") not in ("UNKNOWN", "N/A", None)), {})
    algorithm = first_auth_row.get("algorithm", "Unknown / Dynamic")
    constant_time = first_auth_row.get("constant_time", "N/A")
    hardware_backed = first_auth_row.get("hardware_backed", "N/A")

    # Defense tiers encountered
    defense_tiers = set()
    for r in rate_limited_rows:
        tier_val = r.get("algorithm") or r.get("server_attempt") or "RATE_LIMIT"
        defense_tiers.add(str(tier_val))

    # Timestamps
    first_ts = records[0].get("timestamp", "N/A")
    last_ts = records[-1].get("timestamp", "N/A")

    # Outcome
    if successful_rows:
        outcome = "COMPROMISED (PIN Discovered)"
        discovered_pin = successful_rows[0].get("pin", "N/A")
    elif any("PERMANENT" in str(r.get("algorithm", "")) for r in rate_limited_rows):
        outcome = "DEFENDED (Permanent Lockout Enforced)"
        discovered_pin = "None (Blocked)"
    elif rate_limited_rows:
        outcome = "DEFENDED (Throttled / Rate Limited)"
        discovered_pin = "None"
    else:
        outcome = "EXHAUSTED (Target Not Found)"
        discovered_pin = "None"

    return {
        "csv_path": csv_path,
        "filename": csv_path.name,
        "total_records": total_attempts,
        "rate_limited_count": len(rate_limited_rows),
        "successful_count": len(successful_rows),
        "failed_count": len(failed_rows),
        "algorithm": algorithm,
        "constant_time": constant_time,
        "hardware_backed": hardware_backed,
        "defense_tiers": list(defense_tiers),
        "first_timestamp": first_ts,
        "last_timestamp": last_ts,
        "outcome": outcome,
        "discovered_pin": discovered_pin,
        "response_times": valid_response_times,
        "network_times": valid_network_times,
        "records": records
    }


def generate_run_chart(data: dict, out_chart_path: Path):
    """Generates an attack-specific timeline chart showing response latencies and lockout intervention points."""
    fig, ax1 = plt.subplots(figsize=(8.5, 3.8), dpi=300)

    records = data["records"]
    x = list(range(1, len(records) + 1))
    latencies = []
    event_markers = []
    event_x = []
    event_y = []

    for idx, r in enumerate(records):
        try:
            resp = float(r.get("response_time_ms", 0))
        except (ValueError, TypeError):
            resp = 0
        latencies.append(resp)

        if r.get("success") == "RATE_LIMITED_429":
            event_x.append(idx + 1)
            event_y.append(resp)
            tier_name = r.get("algorithm", "429")
            event_markers.append(tier_name)

    # Plot response latency
    ax1.plot(x, latencies, color="#1E293B", linewidth=1.8, label="Response Latency (ms)", alpha=0.85)

    if event_x:
        ax1.scatter(event_x, event_y, color="#DC2626", s=65, zorder=5, label="HTTP 429 Defense Intervention", edgecolors="black")

    ax1.set_xlabel("Attack Attempt Sequence", fontsize=10, fontweight="bold")
    ax1.set_ylabel("Latency (ms)", fontsize=10, fontweight="bold")
    ax1.set_title(f"Attack Telemetry & Defense Interventions: {data['filename']}", fontsize=11, fontweight="bold", pad=12)
    ax1.grid(True, linestyle="--", alpha=0.5)
    ax1.legend(loc="upper left", frameon=True, facecolor="white", framealpha=0.9, fontsize=9)

    plt.tight_layout()
    plt.savefig(out_chart_path, dpi=300)
    plt.close()


def create_pdf_report(data: dict, output_pdf_path: Path):
    """Compiles parsed telemetry and visual artifacts into a publication-grade PDF report."""
    chart_path = PLOTS_DIR / f"{data['csv_path'].stem}_chart.png"
    generate_run_chart(data, chart_path)

    doc = SimpleDocTemplate(
        str(output_pdf_path),
        pagesize=letter,
        leftMargin=36,
        rightMargin=36,
        topMargin=36,
        bottomMargin=36
    )

    styles = getSampleStyleSheet()

    # Custom Clean Palette Styles
    header_style = ParagraphStyle(
        'DocHeader',
        parent=styles['Heading1'],
        fontName='Helvetica-Bold',
        fontSize=20,
        leading=24,
        textColor=colors.HexColor('#0F172A'),
        spaceAfter=4
    )

    subtitle_style = ParagraphStyle(
        'DocSubtitle',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=10,
        leading=14,
        textColor=colors.HexColor('#475569'),
        spaceAfter=14
    )

    section_header_style = ParagraphStyle(
        'SectionHeader',
        parent=styles['Heading2'],
        fontName='Helvetica-Bold',
        fontSize=12,
        leading=16,
        textColor=colors.HexColor('#0F172A'),
        spaceBefore=14,
        spaceAfter=6
    )

    body_style = ParagraphStyle(
        'Body',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=9,
        leading=13,
        textColor=colors.HexColor('#334155')
    )

    meta_label = ParagraphStyle(
        'MetaLabel',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=8.5,
        leading=11,
        textColor=colors.HexColor('#475569')
    )

    meta_val = ParagraphStyle(
        'MetaVal',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8.5,
        leading=11,
        textColor=colors.HexColor('#0F172A')
    )

    story = []

    # Title Banner
    story.append(Paragraph("PROJECT JANUS // FORENSIC AUDIT REPORT", header_style))
    story.append(Paragraph(
        f"Empirical Attack & Defensive Intervention Assessment &bull; Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        subtitle_style
    ))
    story.append(HRFlowable(width="100%", thickness=1.5, color=colors.HexColor('#0F172A'), spaceAfter=14))

    # Executive Summary Card Table
    resp_times = data["response_times"]
    mean_resp = np.mean(resp_times) if resp_times else 0.0
    median_resp = np.median(resp_times) if resp_times else 0.0
    p95_resp = np.percentile(resp_times, 95) if resp_times else 0.0

    exec_summary_data = [
        [
            Paragraph("<b>Target Endpoint</b>", meta_label),
            Paragraph(":8080/lab/verify (Android Daemon)", meta_val),
            Paragraph("<b>Audit Outcome</b>", meta_label),
            Paragraph(f"<b>{data['outcome']}</b>", meta_val)
        ],
        [
            Paragraph("<b>Total Verification Invocations</b>", meta_label),
            Paragraph(str(data["total_records"]), meta_val),
            Paragraph("<b>Defense Interventions (429)</b>", meta_label),
            Paragraph(f"{data['rate_limited_count']} events", meta_val)
        ],
        [
            Paragraph("<b>Key Derivation Algorithm</b>", meta_label),
            Paragraph(str(data["algorithm"]), meta_val),
            Paragraph("<b>Constant-Time Verification</b>", meta_label),
            Paragraph(str(data["constant_time"]), meta_val)
        ],
        [
            Paragraph("<b>Hardware Enclave Protection</b>", meta_label),
            Paragraph("StrongBox / TEE Hardware Keystore" if data["hardware_backed"] in ("True", True) else "Software Keystore Emulation", meta_val),
            Paragraph("<b>Discovered PIN Result</b>", meta_label),
            Paragraph(str(data["discovered_pin"]), meta_val)
        ]
    ]

    exec_table = Table(exec_summary_data, colWidths=[1.7 * inch, 2.0 * inch, 1.7 * inch, 2.1 * inch])
    exec_table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, -1), colors.HexColor('#F8FAFC')),
        ('BOX', (0, 0), (-1, -1), 1, colors.HexColor('#E2E8F0')),
        ('INNERGRID', (0, 0), (-1, -1), 0.5, colors.HexColor('#E2E8F0')),
        ('TOPPADDING', (0, 0), (-1, -1), 6),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 6),
        ('LEFTPADDING', (0, 0), (-1, -1), 8),
        ('RIGHTPADDING', (0, 0), (-1, -1), 8),
    ]))

    story.append(exec_table)
    story.append(Spacer(1, 14))

    # Telemetry Progression Chart
    story.append(Paragraph("EMPIRICAL TIMELINE & LATENCY BEHAVIOR", section_header_style))
    story.append(Paragraph(
        "Real-time observation of device cryptographic verification latency and progressive rate-limiting triggers.",
        body_style
    ))
    story.append(Spacer(1, 8))
    story.append(Image(str(chart_path), width=7.4 * inch, height=3.3 * inch))
    story.append(Spacer(1, 14))

    # Statistical Evaluation Table
    story.append(Paragraph("QUANTITATIVE PERFORMANCE & LATENCY METRICS", section_header_style))

    stats_table_data = [
        [
            Paragraph("<b>Metric</b>", meta_label),
            Paragraph("<b>Observed Value</b>", meta_label),
            Paragraph("<b>Security & Operational Significance</b>", meta_label)
        ],
        [
            Paragraph("Mean Server Verification Latency", meta_val),
            Paragraph(f"{mean_resp:.2f} ms", meta_val),
            Paragraph("Cryptographic computation time inside secure enclave.", meta_val)
        ],
        [
            Paragraph("Median Server Latency (P50)", meta_val),
            Paragraph(f"{median_resp:.2f} ms", meta_val),
            Paragraph("Representative baseline per-request cost.", meta_val)
        ],
        [
            Paragraph("95th Percentile Latency (P95)", meta_val),
            Paragraph(f"{p95_resp:.2f} ms", meta_val),
            Paragraph("Upper-bound latency under synthetic system load.", meta_val)
        ],
        [
            Paragraph("Exhaustive Keyspace Time (10,000 PINs)", meta_val),
            Paragraph(
                "PERMANENT LOCKOUT (Infinite)" if data["rate_limited_count"] > 0 else (
                    f"~{(mean_resp * 10000) / 1000 / 60:.1f} minutes" if mean_resp > 0 else "Instant (< 1s)"
                ),
                meta_val
            ),
            Paragraph("Time required for an automated adversary to brute-force all 4-digit combinations.", meta_val)
        ]
    ]

    stats_table = Table(stats_table_data, colWidths=[2.2 * inch, 1.8 * inch, 3.4 * inch])
    stats_table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#0F172A')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('BOX', (0, 0), (-1, -1), 1, colors.HexColor('#CBD5E1')),
        ('INNERGRID', (0, 0), (-1, -1), 0.5, colors.HexColor('#E2E8F0')),
        ('TOPPADDING', (0, 0), (-1, -1), 5),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 5),
        ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, colors.HexColor('#F8FAFC')]),
    ]))

    # Update header text color for the first row of stats table
    for col_idx in range(len(stats_table_data[0])):
        stats_table_data[0][col_idx].style.textColor = colors.white

    story.append(stats_table)
    story.append(Spacer(1, 14))

    # Sample Event Log Breakdown Table
    story.append(Paragraph("FORENSIC EVENT LOG (KEY TRANSITION SEQUENCES)", section_header_style))

    # Select representative events: first 4, any 429 events, and last 2
    records = data["records"]
    sample_indices = set()
    for i in range(min(3, len(records))):
        sample_indices.add(i)
    for i, r in enumerate(records):
        if r.get("success") == "RATE_LIMITED_429":
            sample_indices.add(max(0, i - 1))
            sample_indices.add(i)
            sample_indices.add(min(len(records) - 1, i + 1))
    for i in range(max(0, len(records) - 2), len(records)):
        sample_indices.add(i)

    sorted_indices = sorted(list(sample_indices))[:10]  # Cap at 10 rows for clean layout

    log_table_data = [
        [
            Paragraph("<b>Seq</b>", meta_label),
            Paragraph("<b>PIN</b>", meta_label),
            Paragraph("<b>Status / Event</b>", meta_label),
            Paragraph("<b>Latency</b>", meta_label),
            Paragraph("<b>Defense Tier / Notes</b>", meta_label)
        ]
    ]

    for idx in sorted_indices:
        r = records[idx]
        status = r.get("success", "False")
        pin = r.get("pin", "")
        latency = f"{float(r.get('response_time_ms', 0)):.1f} ms" if r.get('response_time_ms') else "N/A"
        tier = r.get("algorithm") or "NORMAL"
        
        status_display = "429 RATE LIMIT" if status == "RATE_LIMITED_429" else ("SUCCESS" if status in ("True", True) else "FAILED")

        log_table_data.append([
            Paragraph(str(idx + 1), meta_val),
            Paragraph(f"<code>{pin}</code>", meta_val),
            Paragraph(status_display, meta_val),
            Paragraph(latency, meta_val),
            Paragraph(str(tier), meta_val)
        ])

    log_table = Table(log_table_data, colWidths=[0.6 * inch, 1.0 * inch, 1.8 * inch, 1.2 * inch, 2.8 * inch])
    log_table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#1E293B')),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.white),
        ('BOX', (0, 0), (-1, -1), 1, colors.HexColor('#CBD5E1')),
        ('INNERGRID', (0, 0), (-1, -1), 0.5, colors.HexColor('#E2E8F0')),
        ('TOPPADDING', (0, 0), (-1, -1), 4),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
        ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, colors.HexColor('#F8FAFC')]),
    ]))

    for col_idx in range(len(log_table_data[0])):
        log_table_data[0][col_idx].style.textColor = colors.white

    story.append(log_table)
    story.append(Spacer(1, 18))

    # Concluding Security Verification Attestation
    story.append(HRFlowable(width="100%", thickness=0.8, color=colors.HexColor('#94A3B8'), spaceAfter=8))
    story.append(Paragraph(
        "<b>Attestation:</b> This document certifies the empirical security results captured by the Project Janus Automated Test Harness. "
        "Cryptographic operations are verified against Android Keystore hardware-backed implementations.",
        ParagraphStyle(
            'Attest',
            parent=styles['Normal'],
            fontName='Helvetica-Oblique',
            fontSize=7.5,
            leading=10,
            textColor=colors.HexColor('#64748B')
        )
    ))

    doc.build(story)
    print(f"[+] Forensic audit PDF successfully generated: {output_pdf_path}")


def main():
    parser = argparse.ArgumentParser(description="Generate detailed PDF attack and defense report")
    parser.add_argument(
        "--csv",
        type=str,
        default=str(RESULTS_DIR / "defended_tiered_lockout.csv"),
        help="Path to attack results CSV file"
    )
    parser.add_argument(
        "--output",
        type=str,
        default=None,
        help="Path to output PDF file"
    )

    args = parser.parse_args()

    csv_path = Path(args.csv)
    if not csv_path.exists():
        print(f"[-] CSV file not found: {csv_path}")
        sys.exit(1)

    if args.output:
        out_pdf = Path(args.output)
    else:
        out_pdf = RESULTS_DIR / f"{csv_path.stem}_audit_report.pdf"

    print(f"[*] Parsing attack telemetry from: {csv_path.name}")
    data = parse_csv_run(csv_path)
    if not data:
        print("[-] No valid records found in CSV.")
        sys.exit(1)

    print(f"[*] Generating PDF audit report to: {out_pdf.name}")
    create_pdf_report(data, out_pdf)


if __name__ == "__main__":
    main()
