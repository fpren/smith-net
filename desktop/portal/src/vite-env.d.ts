/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_SMITHCORE_ENABLED?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
