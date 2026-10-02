/**
 * Shared HTTPS POST helper for Knowledge Gym internal cron endpoints.
 * Bundled into each function zip by Terraform archive_file.
 */
export async function postInternal(path, { baseUrl, token }) {
  const url = new URL(path, baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`);
  const response = await fetch(url, {
    method: "POST",
    headers: {
      "X-Cron-Token": token,
      Accept: "application/json",
    },
  });
  const body = await response.text();
  if (!response.ok) {
    throw new Error(`${path} → HTTP ${response.status}: ${body.slice(0, 500)}`);
  }
  return { status: response.status, body: body.slice(0, 2000) };
}
