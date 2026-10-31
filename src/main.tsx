import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AurigaApp } from "@/components/auriga/AurigaApp";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AurigaApp />
  </StrictMode>,
);
