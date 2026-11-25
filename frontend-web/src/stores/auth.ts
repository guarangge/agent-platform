import { defineStore } from "pinia";
import { fetchCurrentUser, login as loginApi, type CurrentUserInfo, type LoginRequest } from "@/api/auth";
import { clearAccessToken, getAccessToken, setAccessToken } from "@/utils/token";

interface AuthState {
  accessToken: string;
  currentUser: CurrentUserInfo | null;
}

export const useAuthStore = defineStore("auth", {
  state: (): AuthState => ({
    accessToken: getAccessToken(),
    currentUser: null
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.accessToken)
  },
  actions: {
    async login(payload: LoginRequest): Promise<void> {
      const response = await loginApi(payload);
      this.accessToken = response.accessToken;
      setAccessToken(response.accessToken);
      this.currentUser = await fetchCurrentUser();
    },
    async loadCurrentUser(): Promise<void> {
      if (!this.accessToken) {
        return;
      }
      this.currentUser = await fetchCurrentUser();
    },
    logout(): void {
      this.accessToken = "";
      this.currentUser = null;
      clearAccessToken();
    }
  }
});
