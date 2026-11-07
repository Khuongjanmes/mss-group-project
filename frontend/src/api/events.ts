import { api } from './client'
import type { PageResponse } from './types'

export type EventStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'PUBLISHED' | 'REJECTED' | 'CANCELLED' | 'COMPLETED'

export interface Category {
  id: string
  name: string
  slug: string
  description: string | null
}

export interface Venue {
  id: string
  name: string
  address: string
  city: string
  capacity: number | null
}

export interface EventItem {
  id: string
  organizerId: string
  title: string
  description: string | null
  bannerUrl: string | null
  category: Category
  venue: Venue
  startTime: string
  endTime: string
  status: EventStatus
  rejectReason: string | null
  createdAt: string
}

export interface EventSearchParams {
  keyword?: string
  category?: string
  city?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export async function searchEvents(params: EventSearchParams = {}): Promise<PageResponse<EventItem>> {
  const response = await api.get<PageResponse<EventItem>>('/api/events', { params })
  return response.data
}
