"use client";
import { useLocale } from "@/components/locale";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { AuthShell, Field, inputClass, primaryBtnClass } from "@/components/ui";
import { ApiError } from "@/lib/api-client";
import { forgotPassword, resetPassword } from "@/lib/auth";

type Step = 1 | 2 | 3;

export default function ForgotPasswordPage() {
  const { t } = useLocale();
  const [step, setStep] = useState<Step>(1);
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onRequestCode(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const msg = await forgotPassword(email.trim());
      setMessage(msg);
      setStep(2);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Không gửi được mã");
    } finally {
      setBusy(false);
    }
  }

  function onConfirmCode(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (!/^\d{6}$/.test(code.trim())) {
      setError("Mã phải đúng 6 chữ số");
      return;
    }
    setStep(3);
  }

  async function onReset(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const msg = await resetPassword(email.trim(), code.trim(), newPassword);
      setMessage(msg);
      setStep(1);
      setCode("");
      setNewPassword("");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Đặt lại mật khẩu thất bại");
    } finally {
      setBusy(false);
    }
  }

  const subtitle =
    step === 1
      ? "Nhập email để nhận mã 6 số."
      : step === 2
        ? "Nhập mã trong email."
        : "Đặt mật khẩu mới.";

  return (
    <AuthShell title={t("Quên mật khẩu")} subtitle={subtitle}>
      {message && step === 1 ? (
        <p className="mb-4 rounded-sm border border-accent/40 bg-accent/10 px-3 py-2 text-sm text-positive">
          {t(message)}{" "}
          <Link href="/login" className="underline">
            {t("về đăng nhập")}</Link>
        </p>
      ) : null}

      {step === 1 ? (
        <form onSubmit={onRequestCode} className="animate-fade-up-delay">
          <Field label={t("Email")}>
            <input
              className={inputClass}
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Field>
          {error ? (
            <p className="mb-4 text-sm text-warning" role="alert">
              {t(error)}
            </p>
          ) : null}
          <button type="submit" className={primaryBtnClass} disabled={busy}>
            {busy ? t("Đang gửi…") : t("Gửi mã")}
          </button>
        </form>
      ) : null}

      {step === 2 ? (
        <form onSubmit={onConfirmCode} className="animate-fade-up-delay">
          <p className="mb-4 text-sm text-subtle">{t(message)}</p>
          <Field label={t("Mã 6 số")}>
            <input
              className={inputClass}
              inputMode="numeric"
              pattern="\d{6}"
              maxLength={6}
              required
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
            />
          </Field>
          {error ? (
            <p className="mb-4 text-sm text-warning" role="alert">
              {t(error)}
            </p>
          ) : null}
          <button type="submit" className={primaryBtnClass}>
            {t("Tiếp tục")}</button>
        </form>
      ) : null}

      {step === 3 ? (
        <form onSubmit={onReset} className="animate-fade-up-delay">
          <Field label={t("Mật khẩu mới")}>
            <input
              className={inputClass}
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              maxLength={72}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
          </Field>
          {error ? (
            <p className="mb-4 text-sm text-warning" role="alert">
              {t(error)}
            </p>
          ) : null}
          <button type="submit" className={primaryBtnClass} disabled={busy}>
            {busy ? t("Đang lưu…") : t("Đặt lại mật khẩu")}
          </button>
        </form>
      ) : null}

      <p className="mt-6 text-sm text-subtle">
        <Link href="/login" className="hover:text-warning">
          {t("← Quay lại đăng nhập")}</Link>
      </p>
    </AuthShell>
  );
}
