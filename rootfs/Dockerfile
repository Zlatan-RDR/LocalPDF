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

FROM core AS office

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      libreoffice-writer libreoffice-calc libreoffice-impress fonts-liberation \
 && rm -rf /var/lib/apt/lists/* /var/cache/apt/* /usr/share/man /usr/share/info
