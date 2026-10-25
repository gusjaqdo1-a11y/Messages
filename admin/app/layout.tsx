import './globals.css';
import type { ReactNode } from 'react';

export const metadata = { title: 'Messages SOM Admin', description: 'Messages SOM administration console' };

export default function RootLayout({ children }: { children: ReactNode }) {
  return <html lang="en"><body>{children}</body></html>;
}