FROM python:3.12-alpine

RUN apk add --no-cache nodejs jq ca-certificates font-wqy-zenhei \
    && pip install --no-cache-dir reportlab==4.4.10

WORKDIR /workspace
USER 65534:65534

ENTRYPOINT []
CMD ["python3", "--version"]
