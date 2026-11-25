ARG BASE_IMAGE=eclipse-temurin:17-jre-noble
FROM ${BASE_IMAGE}

ARG NODE_VERSION=22.22.0
ARG MCP_FILESYSTEM_VERSION=2026.7.10
ARG MCP_POSTGRES_VERSION=0.6.2
ARG NPM_REGISTRY=https://registry.npmmirror.com/
ARG NODE_DIST_BASE=https://registry.npmmirror.com/-/binary/node

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl xz-utils poppler-utils poppler-data tesseract-ocr tesseract-ocr-chi-sim \
    && case "$(dpkg --print-architecture)" in \
         amd64) node_arch="x64" ;; \
         arm64) node_arch="arm64" ;; \
         *) echo "Unsupported architecture: $(dpkg --print-architecture)" >&2; exit 1 ;; \
       esac \
    && curl -fL --connect-timeout 10 --max-time 180 -o /tmp/node.tar.xz \
         "${NODE_DIST_BASE}/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${node_arch}.tar.xz" \
    && tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1 \
    && rm -rf /var/lib/apt/lists/* /tmp/node.tar.xz

RUN npm config set registry "${NPM_REGISTRY}" \
    && npm config set fetch-retries 2 \
    && npm config set fetch-retry-mintimeout 1000 \
    && npm config set fetch-retry-maxtimeout 10000 \
    && timeout 180s npm install --global --omit=dev --no-audit --no-fund --ignore-scripts --loglevel info \
         "@modelcontextprotocol/server-filesystem@${MCP_FILESYSTEM_VERSION}" \
         "@modelcontextprotocol/server-postgres@${MCP_POSTGRES_VERSION}" \
    && npm cache clean --force

WORKDIR /app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
