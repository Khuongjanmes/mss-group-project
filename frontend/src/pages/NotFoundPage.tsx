import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <section className="py-16 text-center">
      <h1 className="text-2xl font-bold">Không tìm thấy trang</h1>
      <Link to="/" className="mt-4 inline-block font-medium text-accent-foreground hover:underline">
        Về trang chủ
      </Link>
    </section>
  )
}
