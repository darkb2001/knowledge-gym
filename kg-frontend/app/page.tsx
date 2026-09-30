import { redirect } from "next/navigation";

/** Landing: send learners to the question browser (auth gate lives client-side). */
export default function HomePage() {
  redirect("/questions");
}
