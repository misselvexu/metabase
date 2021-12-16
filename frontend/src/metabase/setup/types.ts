export interface Locale {
  name: string;
  code: string;
}

export interface UserInfo {
  first_name: string;
  last_name: string;
  email: string;
  password: string;
  password_confirm: string;
}

export interface DatabaseInfo {
  name: string;
  engine: string;
  details: DatabaseDetails;
}

export interface DatabaseDetails {
  ssl: boolean;
}

export type LocaleData = [string, string];
