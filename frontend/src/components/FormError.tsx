import React from "react";
import type { ApiError } from "../api/ApiError";

interface FormErrorProps {
  error: ApiError | Error | string | null;
}

export const FormError: React.FC<FormErrorProps> = ({ error }) => {
  if (!error) return null;

  // Handle ApiError instance
  if (typeof error === "object" && "title" in error) {
    const apiError = error as ApiError;
    return (
      <div style={{ padding: "1rem", marginBottom: "1rem", border: "1px solid #f5c6cb", borderRadius: "4px", backgroundColor: "#f8d7da", color: "#721c24" }}>
        <strong>{apiError.title}</strong>
        {apiError.detail && <p style={{ margin: "0.5rem 0 0 0", fontSize: "0.9rem" }}>{apiError.detail}</p>}
        {apiError.fieldErrors && apiError.fieldErrors.length > 0 && (
          <ul style={{ margin: "0.5rem 0 0 0", paddingLeft: "1.2rem", fontSize: "0.85rem" }}>
            {apiError.fieldErrors.map((fe, idx) => (
              <li key={idx}>
                <strong>{fe.field}:</strong> {fe.message}
              </li>
            ))}
          </ul>
        )}
      </div>
    );
  }

  // Handle standard Error or raw string
  const message = error instanceof Error ? error.message : String(error);
  return (
    <div style={{ padding: "1rem", marginBottom: "1rem", border: "1px solid #f5c6cb", borderRadius: "4px", backgroundColor: "#f8d7da", color: "#721c24" }}>
      <p style={{ margin: 0, fontSize: "0.9rem" }}>{message}</p>
    </div>
  );
};
