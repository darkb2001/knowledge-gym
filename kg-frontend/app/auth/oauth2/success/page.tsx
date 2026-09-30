"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AuthShell } from "@/components/ui";
import { applyOAuthHash, storeUser } from "@/lib/auth";

/**
 * OAuth2SuccessHandler redirects here with
 * `#accessToken=...&userId=...&role=...&provider=google`
 * Hash is never sent to the server — read client-side only.
 */
export default function OAuthSuccessPage() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    try {
      const parsed = applyOAuthHash(window.location.hash);
      if (!parsed) {
        setError("Thiếu accessToken trong URL hash — thử đăng nhập lại.");
        return;
      }
      // Backend hash only carries userId/role — email/displayName filled when /users/me exists.
      storeUser({
        id: parsed.userId,
        email: "",
        displayName: "Google",
        role: parsed.role,
      });
      router.replace("/questions");
    } finally {
      // Always clear hash so a failed parse cannot leave the token in history.
      window.history.replaceState(null, "", window.location.pathname);
    }
  }, [router]);

  return (
    <AuthShell title="Google" subtitle="Đang hoàn tất đăng nhập…">
      {error ? (
        <p className="text-sm text-ember-400" role="alert">
          {error}
        </p>
      ) : (
        <p className="animate-soft-pulse text-ink-300">Nhận token…</p>
      )}
    </AuthShell>
  );
}
