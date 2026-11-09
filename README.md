# MFIX Printer Control

Android USB utility for Xprinter XP-N160II thermal receipt printers.

## Features
- USB host discovery and permission handling.
- Direct USB bulk transfer; no RawBT dependency.
- Sample receipt / test print.
- Experimental ESC/POS ESC 7 heating profile test.

## Limitations
The public XP-N160II documentation confirms USB and ESC/POS support, but does not document a universal ESC/POS command for persistent print speed or density. This app does not send guessed commands for those settings. Heating profile changes are experimental, sent before a test print, and are not claimed to persist in printer firmware.

## Build
GitHub Actions builds a debug APK on pushes to main. Download the artifact from the Actions run.

## Use
1. Install on Android with USB host/OTG.
2. Connect printer via USB OTG and power it on.
3. Tap connect and grant USB permission.
4. Print a default test first. Change heating only in small increments.

Independent utility; not an official Xprinter product.
