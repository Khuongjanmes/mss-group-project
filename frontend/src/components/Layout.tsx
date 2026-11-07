import { Link, Outlet } from 'react-router-dom'

export default function Layout() {
  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b bg-card">
        <nav className="mx-auto flex max-w-6xl items-center gap-6 px-4 py-3">
          <Link to="/" className="text-lg font-bold text-primary">
            OpenEvent
          </Link>
        </nav>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>
      <footer className="bg-footer py-6 text-center text-sm text-footer-foreground">
        © {new Date().getFullYear()} OpenEvent
      </footer>
    </div>
  )
}
