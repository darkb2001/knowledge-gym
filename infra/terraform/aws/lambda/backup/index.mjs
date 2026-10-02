import { postInternal } from "./http.mjs";

export async function handler() {
  const baseUrl = process.env.KG_API_BASE_URL;
  const token = process.env.CRON_TOKEN;
  if (!baseUrl || !token) {
    throw new Error("KG_API_BASE_URL and CRON_TOKEN are required");
  }
  // 202 Accepted on success from the app; treat any 2xx as OK.
  return postInternal("/api/v1/internal/backup", { baseUrl, token });
}
