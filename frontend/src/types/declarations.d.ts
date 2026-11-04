// Ambient fallback declarations for VS Code TypeScript language server
// Ensures clean resolution of modern bundler-mapped packages across all IDE configurations

declare module 'next/navigation' {
  export * from 'next/dist/client/components/navigation';
}
