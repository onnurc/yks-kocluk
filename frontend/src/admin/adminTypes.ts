import type { PageResponse } from "../messaging/messagingTypes";

export type { PageResponse };
export type UserStatus = "ACTIVE" | "SUSPENDED" | "DELETED";
export type CoachFilter = "ALL" | "APPROVED" | "PENDING" | "REJECTED" | "SUSPENDED";

export interface AdminDashboardSummary {
  totalStudentCount: number; totalCoachCount: number; activeCoachCount: number;
  pendingCoachApplicationCount: number; activeSubscriptionCount: number; salesThisMonthCount: number;
  grossRevenueThisMonth: number; refundAmountThisMonth: number; netCollectedThisMonth: number;
  openReportCount: number; scheduledSessionCount: number; completedSessionCountThisMonth: number;
}
export interface AdminUser { id:number; name:string; email:string; role:"STUDENT"|"COACH"|"ADMIN"; status:UserStatus; emailVerified:boolean; legalOnboardingCompleted:boolean; anonymized:boolean; createdAt:string; }
export interface AdminCoach { id:number; userId:number; coachProfileId:number; name:string; email:string; status:string; approvalState:string; accountStatus:UserStatus; universityId:number|null; university:string|null; department:string|null; publiclyVisible:boolean; createdAt:string; }
export interface CoachStudent { studentId:number; displayName:string; packageId:number; packageName:string; subscriptionStatus:string; subscriptionStart:string; subscriptionEnd:string; sessionsUsedInCurrentWeek:number; sessionsRemainingInCurrentWeek:number; conversationId:number|null; nextSession:{id:number;startTime:string;endTime:string;status:string}|null; }
export interface AdminSession { id:number; type:"PAID"|"TRIAL"; coachProfileId:number; coachUserId:number; coachName:string; studentId:number; studentName:string; startsAt:string; endsAt:string; status:string; subscriptionId:number|null; }
export interface SuspendResponse { userId:number; status:UserStatus; reason:string|null; }
