import { httpClient } from "../api/httpClient";

export interface PublicPackage {
  id: number;
  name: string;
  weeklySessions: number;
  durationDays: number;
  price: number;
}

export const publicPackagesApi = {
  list: (): Promise<PublicPackage[]> =>
    httpClient.get<PublicPackage[]>("/api/v1/public/packages"),
};
