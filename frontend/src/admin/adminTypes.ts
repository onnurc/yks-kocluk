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
export interface AdminUserDetail { user:AdminUser; profileImageUrl:string|null; profileImageAssetId:number|null; }
export interface AdminCoach { id:number; userId:number; coachProfileId:number; name:string; email:string; status:string; approvalState:string; accountStatus:UserStatus; universityId:number|null; university:string|null; department:string|null; publiclyVisible:boolean; profileImageUrl?:string|null; profileImageAssetId?:number|null; introYoutubeVideoId:string|null; createdAt:string; }
export interface CoachYoutubeIntroResponse { coachProfileId:number; videoId:string; embedUrl:string; }
export interface CoachStudent { studentId:number; displayName:string; packageId:number; packageName:string; subscriptionStatus:string; subscriptionStart:string; subscriptionEnd:string; sessionsUsedInCurrentWeek:number; sessionsRemainingInCurrentWeek:number; conversationId:number|null; nextSession:{id:number;startTime:string;endTime:string;status:string}|null; }
export interface AdminSession { id:number; type:"PAID"|"TRIAL"; coachProfileId:number; coachUserId:number; coachName:string; studentId:number; studentName:string; studentEmail?:string; startsAt:string; endsAt:string; status:string; subscriptionId:number|null; meetingUrl?:string|null; }
export interface SuspendResponse { userId:number; status:UserStatus; reason:string|null; }
export interface AdminCoachCreateRequest { fullName:string; email:string; }
export interface AdminCoachCreateResponse { userId:number; coachProfileId:number; fullName:string; email:string; accountStatus:UserStatus; profileStatus:string; }

export type PackageType = "ONE_MONTH" | "THREE_MONTHS" | "UNTIL_EXAM";
export type DiscountType = "PERCENTAGE" | "FIXED_AMOUNT";
export interface AdminPackageCampaign { enabled:boolean; currentlyActive:boolean; title:string; description:string|null; startsAt:string; endsAt:string; discountType:DiscountType; discountValue:number; }
export interface AdminPackageTier { monthsRemaining:number; price:number; }
export interface AdminPackage { id:number; packageType:PackageType; name:string; basePrice:number|null; effectivePrice:number|null; active:boolean; durationMonths:number|null; applicableMonthsRemaining:number|null; evaluationMeetingsPerMonth:number; weeklyMeetingsPerMonth:number; totalMeetingsPerMonth:number; campaign:AdminPackageCampaign|null; priceTiers:AdminPackageTier[]; }
export interface AdminPackageCatalog { yksExamYear:number|null; yksExamDate:string|null; yksExamActive:boolean; applicableMonthsRemaining:number|null; packages:AdminPackage[]; }
export interface AdminCampaignRequest { enabled:boolean; title:string; description:string|null; startsAt:string; endsAt:string; discountType:DiscountType; discountValue:number; }
export interface AdminExamSettingsRequest { examYear:number; examDate:string; active:boolean; }
