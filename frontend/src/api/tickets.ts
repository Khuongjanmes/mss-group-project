import { api } from './client'

export interface TicketType {
  id: string
  eventId: string
  name: string
  price: number
  totalQuantity: number
  soldQuantity: number
  reservedQuantity: number
  /** Còn có thể giữ: tổng trừ đã bán và đang giữ. */
  available: number
  saleStart: string
  saleEnd: string
}

export interface TicketTypeRequest {
  eventId?: string
  name: string
  price: number
  totalQuantity: number
  saleStart: string
  saleEnd: string
}

/** Trạng thái bán của một loại vé tại thời điểm `now`. */
export function saleState(type: TicketType, now = Date.now()): 'upcoming' | 'ended' | 'soldOut' | 'onSale' {
  if (now < Date.parse(type.saleStart)) return 'upcoming'
  if (now >= Date.parse(type.saleEnd)) return 'ended'
  return type.available <= 0 ? 'soldOut' : 'onSale'
}

/** Công khai. */
export async function listTicketTypes(eventId: string): Promise<TicketType[]> {
  return (await api.get<TicketType[]>('/api/ticket-types', { params: { eventId } })).data
}

/** Người quản lý sự kiện; Ticket hỏi Event để xác nhận. */
export async function createTicketType(body: TicketTypeRequest): Promise<TicketType> {
  return (await api.post<TicketType>('/api/ticket-types', body)).data
}

export async function updateTicketType(id: string, body: TicketTypeRequest): Promise<TicketType> {
  return (await api.put<TicketType>(`/api/ticket-types/${id}`, body)).data
}

export async function deleteTicketType(id: string): Promise<void> {
  await api.delete(`/api/ticket-types/${id}`)
}
