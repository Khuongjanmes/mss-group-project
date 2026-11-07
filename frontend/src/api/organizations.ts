import { api } from './client'
import type { EventItem, EventStatus } from './events'
import type { PageResponse } from './types'

/** OWNER, MANAGER quản lý mọi sự kiện của tổ chức; MEMBER chỉ sự kiện mình làm HOST (BR-14). */
export type OrganizationRole = 'OWNER' | 'MANAGER' | 'MEMBER'

export interface Organization {
  id: string
  name: string
  description: string | null
  /** Vai trò của người đang đăng nhập. */
  myRole: OrganizationRole
  /** Người đang đăng nhập có được tạo sự kiện cho tổ chức không. */
  canCreateEvents: boolean
  memberCount: number
  createdAt: string
}

export interface Member {
  userId: string
  /** `null` khi Identity tạm không phản hồi. */
  email: string | null
  fullName: string | null
  role: OrganizationRole
  canCreateEvents: boolean
}

export interface OrganizationRequest {
  name: string
  description: string | null
}

export async function listMyOrganizations(): Promise<Organization[]> {
  return (await api.get<Organization[]>('/api/organizations/my')).data
}

export async function createOrganization(body: OrganizationRequest): Promise<Organization> {
  return (await api.post<Organization>('/api/organizations', body)).data
}

export async function getOrganization(id: string): Promise<Organization> {
  return (await api.get<Organization>(`/api/organizations/${id}`)).data
}

export async function updateOrganization(id: string, body: OrganizationRequest): Promise<Organization> {
  return (await api.put<Organization>(`/api/organizations/${id}`, body)).data
}

export async function listMembers(id: string): Promise<Member[]> {
  return (await api.get<Member[]>(`/api/organizations/${id}/members`)).data
}

/** Mời bằng email tài khoản OpenEvent; MANAGER chỉ mời được MEMBER. */
export async function inviteMember(id: string, email: string, role: Exclude<OrganizationRole, 'OWNER'>) {
  return (await api.post<Member>(`/api/organizations/${id}/members`, { email, role })).data
}

/** Chỉ OWNER: đổi vai trò và quyền tạo sự kiện của MEMBER. */
export async function changeMember(
  id: string,
  userId: string,
  body: { role: Exclude<OrganizationRole, 'OWNER'>; canCreateEvents: boolean },
) {
  return (await api.put<Member>(`/api/organizations/${id}/members/${userId}`, body)).data
}

export async function removeMember(id: string, userId: string): Promise<void> {
  await api.delete(`/api/organizations/${id}/members/${userId}`)
}

export async function transferOwnership(id: string, userId: string): Promise<Organization> {
  return (await api.put<Organization>(`/api/organizations/${id}/owner`, { userId })).data
}

export async function listOrganizationEvents(
  id: string,
  params: { status?: EventStatus; page?: number; size?: number },
): Promise<PageResponse<EventItem>> {
  return (await api.get<PageResponse<EventItem>>(`/api/organizations/${id}/events`, { params })).data
}

export const ORGANIZATION_ROLE_LABELS: Record<OrganizationRole, string> = {
  OWNER: 'Chủ sở hữu',
  MANAGER: 'Quản lý',
  MEMBER: 'Thành viên',
}

/** HOST, hoặc OWNER/MANAGER của tổ chức sở hữu sự kiện, mới sửa, gửi duyệt, hủy được (BR-06). */
export function canManageEvent(event: EventItem, userId: string | undefined, organizations: Organization[]): boolean {
  if (event.organizerId === userId) return true
  const organization = organizations.find((item) => item.id === event.organizationId)
  return organization !== undefined && organization.myRole !== 'MEMBER'
}
