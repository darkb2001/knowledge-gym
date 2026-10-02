import { redirect } from "next/navigation";

/** Topic-first entry; session restoration remains in the client-side auth gate. */
export default function HomePage() {
  redirect("/learn");
}
