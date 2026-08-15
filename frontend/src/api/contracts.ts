export type EventStatus =
  | 'DRAFT'
  | 'PUBLISHED'
  | 'SALES_OPEN'
  | 'SOLD_OUT'
  | 'CANCELLED'
  | 'COMPLETED'
  | 'ARCHIVED'

export type TicketTypeStatus = 'ACTIVE' | 'PAUSED' | 'ARCHIVED'

export interface EventCategory {
  id: string
  slug: string
  name: string
  description: string | null
  status: 'ACTIVE' | 'ARCHIVED'
}

export interface PublicTicketType {
  id: string
  name: string
  description: string | null
  price: number
  currency: string
  allocation: number
  availableQuantity: number
  salesStart: string
  salesEnd: string
  minQuantity: number
  maxQuantity: number
  status: TicketTypeStatus
  onSale: boolean
}

export interface PublicEventSummary {
  id: string
  title: string
  category: EventCategory
  timezone: string
  startsAt: string
  endsAt: string
  venueId: string
  venueSpaceId: string | null
  capacity: number
  status: EventStatus
}

export interface PublicEventDetail extends PublicEventSummary {
  description: string
  ticketTypes: PublicTicketType[]
  publishedAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface VenueAddress {
  addressLine1: string
  addressLine2: string | null
  city: string
  region: string | null
  postalCode: string | null
  countryCode: string
  latitude: number | null
  longitude: number | null
}

export interface VenueSpace {
  id: string
  venueId: string
  name: string
  description: string | null
  capacity: number
  status: 'ACTIVE' | 'ARCHIVED'
  amenities: string[]
}

export interface Venue {
  id: string
  organizerId: string
  name: string
  description: string | null
  address: VenueAddress
  timezone: string
  totalCapacity: number
  status: 'ACTIVE' | 'ARCHIVED'
  amenities: string[]
  metadata: Record<string, string>
  spaces: VenueSpace[]
}
