import { apiRequest } from "./api-client";

type PresignedUpload = {
  bucket: string;
  objectKey: string;
  url: string;
  publicUrl: string;
  expiresInSeconds: number;
};

export async function uploadAvatar(file: File): Promise<string> {
  const params = new URLSearchParams({
    bucket: "kg-avatars",
    filename: file.name,
    contentType: file.type || "application/octet-stream",
  });
  const presigned = await apiRequest<PresignedUpload>(`/storage/presigned-put?${params}`, {
    method: "POST",
  });
  const response = await fetch(presigned.url, {
    method: "PUT",
    headers: { "Content-Type": file.type || "application/octet-stream" },
    body: file,
  });
  if (!response.ok) {
    throw new Error(`Upload failed with HTTP ${response.status}`);
  }
  return presigned.publicUrl;
}
