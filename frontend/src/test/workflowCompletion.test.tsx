import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CoachTrialAvailabilityPage } from "../pages/CoachTrialAvailabilityPage";
import { rollingTrialDays } from "../pages/trialAvailabilityDates";
import { AdminSessionsPage } from "../pages/admin/AdminSessionsPage";
import { RefundRequestAction } from "../refunds/RefundRequestAction";
import { ReportModal } from "../safety/ReportModal";

const mocks=vi.hoisted(()=>({
  getTrialAvailability:vi.fn(),saveTrialAvailability:vi.fn(),getTrialConsultations:vi.fn(),
  sessions:vi.fn(),confirmTrial:vi.fn(),eligibility:vi.fn(),createRefund:vi.fn(),createReport:vi.fn(),
}));
vi.mock("../coachDashboard/coachDashboardApi",()=>({coachDashboardApi:{getTrialAvailability:mocks.getTrialAvailability,saveTrialAvailability:mocks.saveTrialAvailability,getTrialConsultations:mocks.getTrialConsultations}}));
vi.mock("../admin/adminApi",()=>({adminApi:{sessions:mocks.sessions,confirmTrial:mocks.confirmTrial}}));
vi.mock("../refunds/refundRequestApi",()=>({refundRequestApi:{eligibility:mocks.eligibility,create:mocks.createRefund}}));
vi.mock("../safety/safetyApi",()=>({safetyApi:{createReport:mocks.createReport}}));

afterEach(cleanup);
beforeEach(()=>{vi.clearAllMocks();mocks.getTrialAvailability.mockResolvedValue([]);mocks.getTrialConsultations.mockResolvedValue([]);mocks.saveTrialAvailability.mockResolvedValue([]);mocks.sessions.mockResolvedValue({content:[],page:0,size:20,totalElements:0,totalPages:0,last:true});});

describe("trial, subscription and moderation workflow completion",()=>{
  it("builds a rolling seven-calendar-day window including today",()=>{
    const days=rollingTrialDays(new Date("2026-08-31T08:00:00Z"));
    expect(days).toHaveLength(7); expect(days[0].toISOString()).toBe("2026-08-31T00:00:00.000Z"); expect(days[6].toISOString()).toBe("2026-09-06T00:00:00.000Z");
  });

  it("loads coach trial availability, preserves booked selections and saves server-side",async()=>{
    const tomorrow=new Date(Date.now()+86400000); const day=rollingTrialDays(tomorrow)[0];
    const start=new Date(Date.UTC(day.getUTCFullYear(),day.getUTCMonth(),day.getUTCDate(),7,0)).toISOString();
    mocks.getTrialAvailability.mockResolvedValue([{id:1,coachProfileId:2,startTime:start,endTime:new Date(Date.parse(start)+1800000).toISOString(),booked:true,purpose:"TRIAL"}]);
    mocks.saveTrialAvailability.mockResolvedValue([{id:1,coachProfileId:2,startTime:start,endTime:new Date(Date.parse(start)+1800000).toISOString(),booked:true,purpose:"TRIAL"}]);
    render(<CoachTrialAvailabilityPage/>);
    expect(await screen.findByText("Ücretsiz Görüşme Talepleri")).toBeInTheDocument();
    const booked=await screen.findByTitle("Bu saat rezerve edildi"); expect(booked).toBeDisabled(); expect(booked).toHaveAttribute("aria-pressed","true");
    fireEvent.click(screen.getByRole("button",{name:"Seçimi Kaydet"}));
    await waitFor(()=>expect(mocks.saveTrialAvailability).toHaveBeenCalledWith([start]));
  });

  it("lets admin confirm a requested trial with an HTTPS meeting URL",async()=>{
    mocks.sessions.mockResolvedValue({content:[{id:9,type:"TRIAL",coachProfileId:7,coachUserId:70,coachName:"Derya Koç",studentId:4,studentName:"Can Öğrenci",studentEmail:"can@example.com",startsAt:"2026-09-01T10:00:00Z",endsAt:"2026-09-01T10:30:00Z",status:"REQUESTED",subscriptionId:null,meetingUrl:null}],page:0,size:20,totalElements:1,totalPages:1,last:true});
    mocks.confirmTrial.mockResolvedValue({}); render(<AdminSessionsPage/>);
    fireEvent.click(await screen.findByRole("button",{name:"Bağlantı Ekle ve Onayla"}));
    fireEvent.change(screen.getByLabelText("HTTPS görüşme bağlantısı"),{target:{value:"https://meet.google.com/abc-defg-hij"}});
    fireEvent.click(screen.getByRole("button",{name:"Onayla ve Bildir"}));
    await waitFor(()=>expect(mocks.confirmTrial).toHaveBeenCalledWith(9,"https://meet.google.com/abc-defg-hij"));
    expect(await screen.findByText("CONFIRMED")).toBeInTheDocument();
  });

  it("uses backend eligibility and completes the student refund without admin approval",async()=>{
    mocks.eligibility.mockResolvedValue({subscriptionId:41,eligible:true,status:"ELIGIBLE",refundableAmount:2450,currency:"TRY",deadline:null,explanation:"İptal ve iade hesabı hazır.",activeRequestStatus:null,packageType:"THREE_MONTHS",policy:"THREE_MONTHS_RAW_ONE_MONTH",cancellationRequestedAt:"2026-08-29T10:00:00Z",usedMonthCount:1,currentServicePeriodEnd:"2026-09-10T10:00:00Z",accessEndsAt:"2026-09-10T10:00:00Z",consumedAmount:3000});
    mocks.createRefund.mockResolvedValue({id:3,status:"REFUNDED"}); const refresh=vi.fn(); render(<RefundRequestAction subscriptionId={41} onSuccess={refresh}/>);
    fireEvent.click(await screen.findByRole("button",{name:"İptal ve İade Talebi"})); const dialog=screen.getByRole("dialog",{name:"İptal ve iadeyi doğrula"}); expect(dialog).toHaveTextContent(/2\.450/); expect(dialog).toHaveTextContent(/hizmet ayının sonuna kadar devam eder/);
    fireEvent.click(within(dialog).getByRole("button",{name:"İptal ve İadeyi Onayla"}));
    await waitFor(()=>expect(mocks.createRefund).toHaveBeenCalledWith(41)); expect(refresh).toHaveBeenCalled(); expect(await screen.findByText(/Erişiminiz 10 Eylül 2026 tarihine kadar devam eder/)).toBeInTheDocument();
  });

  it("renders the report flow with product classes and keeps the existing API contract",async()=>{
    mocks.createReport.mockResolvedValue({}); render(<ReportModal targetType="USER" targetId={8} onClose={vi.fn()}/>);
    expect(screen.getByRole("dialog",{name:"Kullanıcıyı Bildir"})).toHaveClass("report-modal");
    fireEvent.change(screen.getByLabelText(/Detaylar/),{target:{value:"Güvenlik açıklaması"}}); fireEvent.click(screen.getByRole("button",{name:"Bildirimi Gönder"}));
    await waitFor(()=>expect(mocks.createReport).toHaveBeenCalledWith("USER",8,"Kaba / Uygunsuz Davranış","Güvenlik açıklaması"));
  });
});
