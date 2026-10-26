FROM --platform=linux/arm64 debian:trixie-slim AS core

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      bash ca-certificates curl tzdata fontconfig fonts-dejavu-core \
      libfreetype6 \
      qpdf ghostscript \
      tesseract-ocr tesseract-ocr-eng tesseract-ocr-ara \
 && rm -rf /var/lib/apt/lists/* /var/cache/apt/* /usr/share/man /usr/share/info

RUN mkdir -p /opt/java/openjdk \
 && curl -fsSL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/aarch64/jre/hotspot/normal/eclipse" -o /tmp/jre.tar.gz \
 && tar -xzf /tmp/jre.tar.gz -C /opt/java/openjdk --strip-components=1 \
 && rm -f /tmp/jre.tar.gz \
 && mkdir -p /usr/lib/jvm \
 && ln -s /opt/java/openjdk /usr/lib/jvm/default-jvm \
 && ln -s /opt/java/openjdk/bin/java /usr/local/bin/java \
 && ln -s /usr/share/tesseract-ocr/5/tessdata /usr/share/tessdata \
 && mkdir -p /opt/stirling /data /work \
 && chmod 1777 /work

# JPDFium linux-arm64 natives (downloaded by the workflow into rootfs/natives).
# StirlingServer.kt adds this folder to the classpath when it contains a .jar.
RUN mkdir -p /opt/stirling/natives
COPY natives/ /opt/stirling/natives/

# ---------------------------------------------------------------------------
# tools pack: Python (venv with OpenCV, WeasyPrint), poppler (pdftohtml),
# ImageMagick, OCRmyPDF.  Shipped to the phone as a delta over "core".
# ---------------------------------------------------------------------------
FROM core AS tools

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      python3 python3-venv \
      poppler-utils imagemagick ocrmypdf unpaper \
      libpango-1.0-0 libpangoft2-1.0-0 libharfbuzz-subset0 libgdk-pixbuf-2.0-0 libcairo2 \
 && python3 -m venv --system-site-packages /opt/venv \
 && /opt/venv/bin/pip install --no-cache-dir weasyprint opencv-python-headless numpy pillow fonttools \
 && ln -s /opt/venv/bin/weasyprint /usr/local/bin/weasyprint \
 && printf '#!/bin/sh\nexec /opt/venv/bin/python3 "$@"\n' > /usr/local/bin/python3 \
 && chmod 755 /usr/local/bin/python3 \
 && rm -rf /var/lib/apt/lists/* /var/cache/apt/* /root/.cache /usr/share/man /usr/share/info

# ---------------------------------------------------------------------------
# office pack: LibreOffice + unoserver.  Delta over "tools" (install tools first).
# ---------------------------------------------------------------------------
FROM tools AS office

ENV DEBIAN_FRONTEND=noninteractive

# The python3 wrapper is removed while apt runs so package scripts see the system python.
RUN rm -f /usr/local/bin/python3 \
 && apt-get update \
 && apt-get install -y --no-install-recommends \
      libreoffice-writer libreoffice-calc libreoffice-impress python3-uno fonts-liberation \
 && /opt/venv/bin/pip install --no-cache-dir unoserver \
 && ln -s /opt/venv/bin/unoconvert /usr/local/bin/unoconvert \
 && printf '#!/bin/sh\nexec /opt/venv/bin/python3 "$@"\n' > /usr/local/bin/python3 \
 && chmod 755 /usr/local/bin/python3 \
 && rm -rf /var/lib/apt/lists/* /var/cache/apt/* /root/.cache /usr/share/man /usr/share/info
