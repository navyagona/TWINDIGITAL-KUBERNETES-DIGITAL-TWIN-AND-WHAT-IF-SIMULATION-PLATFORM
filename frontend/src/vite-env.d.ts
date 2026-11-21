/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Optional absolute gateway URL (e.g. http://localhost:3000). Empty = same-origin /api via proxy. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module '*.css' {
  const content: Record<string, string>;
  export default content;
}
