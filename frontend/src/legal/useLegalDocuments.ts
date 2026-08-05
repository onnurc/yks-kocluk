import { useCallback, useEffect, useMemo, useState } from "react";
import { legalApi } from "./legalApi";
import type { LegalDocument, LegalDocumentType } from "./legalTypes";

type DocumentMap = Partial<Record<LegalDocumentType, LegalDocument>>;

export const useLegalDocuments = (types: LegalDocumentType[]) => {
  const typesKey = types.join("|");
  const stableTypes = useMemo(() => typesKey.split("|") as LegalDocumentType[], [typesKey]);
  const [documents, setDocuments] = useState<DocumentMap>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const loaded = await Promise.all(stableTypes.map((type) => legalApi.getCurrent(type)));
      setDocuments(
        loaded.reduce<DocumentMap>((result, document) => {
          result[document.type] = document;
          return result;
        }, {})
      );
    } catch (cause) {
      setDocuments({});
      setError(cause instanceof Error ? cause : new Error("Hukuki metinler yüklenemedi."));
    } finally {
      setLoading(false);
    }
  }, [stableTypes]);

  useEffect(() => {
    // Data fetching is the external synchronization performed by this hook.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void reload();
  }, [reload]);

  const ready = !loading && !error && stableTypes.every((type) => Boolean(documents[type]));

  return { documents, loading, error, ready, reload };
};
