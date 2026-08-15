const services = [
  ['API gateway', 'Public routing and cross-cutting policies'],
  ['Authentication', 'Identity and access boundary'],
  ['Events', 'Lifecycle, discovery, and ticket inventory'],
  ['Venues', 'Rooms, capacity, and availability'],
  ['Attendees', 'Attendance and future booking boundary'],
  ['Payments', 'Payment provider boundary'],
  ['Notifications', 'Delivery provider boundary'],
] as const

const foundations = ['PostgreSQL', 'Redis', 'Apache Kafka', 'OpenTelemetry']

function App() {
  return (
    <main className="min-h-screen overflow-hidden bg-[#f3f5ef] text-[#17211b]">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-96 bg-[radial-gradient(circle_at_top_right,rgba(238,135,82,0.28),transparent_48%),radial-gradient(circle_at_top_left,rgba(69,118,92,0.18),transparent_42%)]" />

      <section className="relative mx-auto flex min-h-screen max-w-7xl flex-col px-6 py-8 sm:px-10 lg:px-16">
        <header className="flex items-center justify-between border-b border-[#17211b]/10 pb-5">
          <a className="flex items-center gap-3 font-semibold tracking-tight" href="/">
            <span className="grid size-10 place-items-center rounded-full bg-[#1f5038] text-sm text-white">EM</span>
            <span>Event Management Platform</span>
          </a>
          <span className="rounded-full border border-[#1f5038]/20 bg-white/60 px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em] text-[#1f5038] backdrop-blur">
            Phase 3
          </span>
        </header>

        <div className="grid flex-1 items-center gap-14 py-16 lg:grid-cols-[1.05fr_0.95fr] lg:py-20">
          <div>
            <p className="mb-5 text-sm font-semibold uppercase tracking-[0.24em] text-[#c65e2f]">Local first. Cloud ready.</p>
            <h1 className="max-w-3xl text-5xl font-semibold leading-[0.98] tracking-[-0.055em] sm:text-6xl lg:text-7xl">
              A clean foundation for remarkable events.
            </h1>
            <p className="mt-7 max-w-2xl text-lg leading-8 text-[#46534b]">
              The platform shell is ready for iterative delivery: independently owned services, versioned APIs,
              reliable local infrastructure, and observability from the first request.
            </p>

            <div className="mt-10 flex flex-wrap gap-3">
              {foundations.map((foundation) => (
                <span key={foundation} className="rounded-full bg-[#17211b] px-4 py-2 text-sm font-medium text-[#f8f4e8]">
                  {foundation}
                </span>
              ))}
            </div>

            <div className="mt-12 flex items-center gap-4 text-sm text-[#5c675f]">
              <span className="relative flex size-3">
                <span className="absolute inline-flex size-full animate-ping rounded-full bg-[#5c9e74] opacity-60" />
                <span className="relative inline-flex size-3 rounded-full bg-[#397b51]" />
              </span>
              Venue and event capabilities are ready. Booking and payment remain deferred.
            </div>
          </div>

          <div className="rounded-[2rem] border border-white/70 bg-white/70 p-5 shadow-[0_30px_90px_rgba(41,54,45,0.12)] backdrop-blur-md sm:p-7">
            <div className="mb-5 flex items-center justify-between px-2">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.2em] text-[#788078]">Service map</p>
                <h2 className="mt-1 text-xl font-semibold tracking-tight">Independent boundaries</h2>
              </div>
              <span className="rounded-full bg-[#e8efe8] px-3 py-1 text-xs font-medium text-[#316447]">7 services</span>
            </div>

            <div className="space-y-2">
              {services.map(([name, description], index) => (
                <article
                  key={name}
                  className="group flex items-center gap-4 rounded-2xl border border-[#17211b]/[0.06] bg-[#fafbf7] p-4 transition hover:-translate-y-0.5 hover:border-[#1f5038]/20 hover:shadow-md"
                >
                  <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-[#f1d7c6] text-xs font-bold text-[#9d4825]">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <div>
                    <h3 className="font-semibold tracking-tight">{name}</h3>
                    <p className="mt-0.5 text-sm text-[#687169]">{description}</p>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </div>

        <footer className="flex flex-col gap-2 border-t border-[#17211b]/10 pt-5 text-xs text-[#687169] sm:flex-row sm:items-center sm:justify-between">
          <span>React · TypeScript · Tailwind CSS</span>
          <span>Public APIs begin at /api/v1</span>
        </footer>
      </section>
    </main>
  )
}

export default App
