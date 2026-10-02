"use client";
import { useLocale } from "@/components/locale";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import {
  AuthShell,
  Field,
  ghostBtnClass,
  inputClass,
  primaryBtnClass,
} from "@/components/ui";
import { ApiError } from "@/lib/api-client";
import { register, startGoogleLogin } from "@/lib/auth";

export default function RegisterPage() {
  const { t } = useLocale();
  const router = useRouter();
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await register(email.trim(), password, displayName.trim());
      router.replace("/learn");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Đăng ký thất bại");
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title={t("Tạo tài khoản")} subtitle={t("Email + mật khẩu ít nhất 8 ký tự.")}>
      <form onSubmit={onSubmit} className="animate-fade-up-delay">
        <Field label={t("Tên hiển thị")}>
          <input
            className={inputClass}
            required
            maxLength={100}
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
          />
        </Field>
        <Field label={t("Email")}>
          <input
            className={inputClass}
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </Field>
        <Field label={t("Mật khẩu")}>
          <input
            className={inputClass}
            type="password"
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={72}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        {error ? (
          <p className="mb-4 text-sm text-warning" role="alert">
            {t(error)}
          </p>
        ) : null}
        <button type="submit" className={primaryBtnClass} disabled={busy}>
          {busy ? t("Đang tạo…") : t("Đăng ký")}
        </button>
      </form>

      <button type="button" className={`${ghostBtnClass} mt-3`} onClick={startGoogleLogin}>
        {t("Đăng ký với Google")}</button>

      <p className="mt-6 text-sm text-subtle">
        {t("Đã có tài khoản?")}{" "}
        <Link href="/login" className="text-positive underline underline-offset-4 hover:text-strong">
          {t("Đăng nhập")}</Link>
      </p>
    </AuthShell>
  );
}
