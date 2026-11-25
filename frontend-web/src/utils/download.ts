import type { AxiosResponse } from "axios";

function parseFileName(contentDisposition?: string): string | null {
  if (!contentDisposition) {
    return null;
  }
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1]);
  }
  const plainMatch = contentDisposition.match(/filename="?([^"]+)"?/i);
  return plainMatch?.[1] ?? null;
}

export function triggerBlobDownload(response: AxiosResponse<Blob>, fallbackFileName: string): void {
  const fileName = parseFileName(response.headers["content-disposition"]) ?? fallbackFileName;
  triggerDownloadedBlob(response.data, fileName);
}

/** 保存 SDK 或 Axios 获取的 Blob。 */
export function triggerDownloadedBlob(data: Blob, fileName: string): void {
  const blobUrl = window.URL.createObjectURL(data);
  const link = document.createElement("a");
  link.href = blobUrl;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(blobUrl);
}
