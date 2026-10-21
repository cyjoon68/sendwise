/// <reference types="vite/client" />

export {};

declare module "vue-router" {
  interface RouteMeta {
    auth?: boolean;
  }
}

declare global {
  interface ImportMetaEnv {
    readonly VITE_USE_MOCK?: string;
    readonly VITE_API_BASE?: string;
  }
}
