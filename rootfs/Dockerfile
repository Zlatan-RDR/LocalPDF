FROM --platform=linux/arm64 alpine:3.23 AS core

RUN apk add --no-cache \
      bash ca-certificates tzdata fontconfig ttf-dejavu \
      openjdk25-jre-headless \
      qpdf ghostscript \
      tesseract-ocr tesseract-ocr-data-eng tesseract-ocr-data-ara \
 && (apk add --no-cache font-noto-arabic || echo "font-noto-arabic is not available") \
 && rm -rf /usr/share/man /usr/share/doc /usr/share/info /usr/share/licenses \
 && mkdir -p /opt/stirling /data /work \
 && chmod 1777 /work

FROM core AS office

RUN apk add --no-cache \
      libreoffice-common libreoffice-writer libreoffice-calc libreoffice-impress \
      font-liberation \
 && rm -rf /usr/share/man /usr/share/doc /usr/share/info /usr/share/licenses
