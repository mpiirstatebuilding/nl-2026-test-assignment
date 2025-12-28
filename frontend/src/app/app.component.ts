import {Component} from "@angular/core";
import {CommonModule} from "@angular/common";
import {FormsModule} from "@angular/forms";
import {ActionResult, Book, LibraryApiService, Member, MemberSummary, OverdueBook,} from "./library.service";
import {t as translate} from "./i18n";

@Component({
  selector: "app-root",
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: "./app.component.html",
  styleUrls: ["./app.component.css"],
})
export class AppComponent {
  books: Book[] = [];
  members: Member[] = [];
  overdueBooks: OverdueBook[] = [];
  memberSummary: MemberSummary | null = null;
  selectedBookId: string | null = null;
  selectedMemberId: string | null = null;
  bookIdInput = "";
  bookTitleInput = "";
  memberIdInput = "";
  memberNameInput = "";
  bookModalOpen = false;
  bookModalMode: "create" | "edit" = "create";
  memberModalOpen = false;
  memberModalMode: "create" | "edit" = "create";
  extensionModalOpen = false;
  extensionDays = 1;
  lastMessage = translate("statusIdle");
  loading = false;
  apiAvailable = true;
  bookModalError: string | null = null;
  memberModalError: string | null = null;

  readonly MIN_EXTENSION_DAYS = 1;
  readonly MAX_EXTENSION_DAYS = 90;
  private readonly api = new LibraryApiService();

  constructor() {
    this.refreshAll();
  }

  t(key: string): string {
    return translate(key);
  }

  async refreshAll(): Promise<void> {
    this.loading = true;
    try {
      const [books, members] = await Promise.all([
        this.api.books(),
        this.api.members(),
      ]);
      this.apiAvailable = true;
      this.books = [...books].sort((a, b) => a.id.localeCompare(b.id));
      this.members = [...members].sort((a, b) => a.id.localeCompare(b.id));
      const bookStillExists =
        this.selectedBookId &&
        this.books.some((b) => b.id === this.selectedBookId);
      const memberStillExists =
        this.selectedMemberId &&
        this.members.some((m) => m.id === this.selectedMemberId);
      if (!bookStillExists) {
        this.selectedBookId = this.books.length ? this.books[0].id : null;
      }
      if (!memberStillExists) {
        this.selectedMemberId = this.members.length ? this.members[0].id : null;
      }
      this.syncInputsWithSelection();
      this.lastMessage = this.lastMessage || this.t("sampleData");
    } catch (e) {
      this.apiAvailable = false;
      this.lastMessage = this.t("apiOffline");
    } finally {
      this.loading = false;
    }
  }

  async borrow(): Promise<void> {
    if (!this.selectedBookId || !this.selectedMemberId) {
      return;
    }
    await this.runAction(() =>
      this.api.borrow(this.selectedBookId!, this.selectedMemberId!),
    );
  }

  async reserve(): Promise<void> {
    if (!this.selectedBookId || !this.selectedMemberId) {
      return;
    }
    await this.runAction(() =>
      this.api.reserve(this.selectedBookId!, this.selectedMemberId!),
    );
  }

  async cancelReservation(): Promise<void> {
    if (!this.selectedBookId || !this.selectedMemberId) {
      return;
    }
    await this.runAction(() =>
      this.api.cancelReservation(this.selectedBookId!, this.selectedMemberId!),
    );
  }

  async returnBook(): Promise<void> {
    if (!this.selectedBookId || !this.selectedMemberId) {
      return;
    }
    // Get the book to verify it's loaned
    const book = this.activeBook;
    if (!book || !book.loanedTo) {
      this.lastMessage = this.t("NOT_LOANED");
      return;
    }
    // Pass the SELECTED member's ID - backend will verify they are the current borrower
    await this.runAction(() =>
      this.api.returnBook(this.selectedBookId!, this.selectedMemberId!),
    );
  }

  openExtensionModal(): void {
    if (!this.selectedBookId || !this.selectedMemberId) {
      return;
    }
    this.extensionDays = this.MIN_EXTENSION_DAYS; // Reset to default
    this.extensionModalOpen = true;
  }

  closeExtensionModal(): void {
    this.extensionModalOpen = false;
    this.extensionDays = this.MIN_EXTENSION_DAYS;
  }

  async submitExtensionModal(): Promise<void> {
    if (
      this.extensionDays < this.MIN_EXTENSION_DAYS ||
      this.extensionDays > this.MAX_EXTENSION_DAYS
    ) {
      this.lastMessage = `Extension must be between ${this.MIN_EXTENSION_DAYS} and ${this.MAX_EXTENSION_DAYS} days`;
      return;
    }

    await this.runAction(() =>
      this.api.extendLoan(
        this.selectedBookId!,
        this.selectedMemberId!,
        this.extensionDays,
      ),
    );
    this.closeExtensionModal();
  }

  get currentDueDate(): string | null {
    return this.activeBook?.dueDate || null;
  }

  get firstDueDate(): string | null {
    return this.activeBook?.firstDueDate || null;
  }

  get maxExtensionDays(): number | null {
    if (!this.currentDueDate || !this.firstDueDate) return null;
    const current = new Date(this.currentDueDate);
    const firstDate = new Date(this.firstDueDate);
    let diff = (current.getTime() - firstDate.getTime()) / (1000 * 60 * 60 * 24);  // Difference in days
    return this.MAX_EXTENSION_DAYS - diff;
  }

  get newDueDate(): string | null {
    if (!this.currentDueDate) return null;
    const current = new Date(this.currentDueDate);
    const newDate = new Date(current);
    newDate.setDate(current.getDate() + this.extensionDays);
    return newDate.toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  }

  async createBook(): Promise<void> {
    if (!this.bookIdInput || !this.bookTitleInput) {
      this.bookModalError = "INVALID_REQUEST";
      return;
    }
    this.bookModalError = null;
    this.loading = true;
    try {
      const result = await this.api.createBook(
        this.bookIdInput,
        this.bookTitleInput,
      );
      if (!result.ok) {
        this.bookModalError = result.reason ?? "INVALID_REQUEST";
        return;
      }
      this.lastMessage = this.t("ok");
      this.closeBookModal();
      await this.refreshAll();
    } catch (e) {
      this.bookModalError = "apiError";
    } finally {
      this.loading = false;
    }
  }

  async updateBook(): Promise<void> {
    if (!this.bookIdInput || !this.bookTitleInput) {
      this.lastMessage = this.t("INVALID_REQUEST");
      return;
    }
    await this.runAction(() =>
      this.api.updateBook(this.bookIdInput, this.bookTitleInput),
    );
    this.clearBookModal();
  }

  async deleteBook(id: string): Promise<void> {
    if (!id) {
      this.lastMessage = this.t("INVALID_REQUEST");
      return;
    }
    await this.runAction(() => this.api.deleteBook(id));
  }

  async createMember(): Promise<void> {
    if (!this.memberIdInput || !this.memberNameInput) {
      this.memberModalError = "INVALID_REQUEST";
      return;
    }
    this.memberModalError = null;
    this.loading = true;
    try {
      const result = await this.api.createMember(
        this.memberIdInput,
        this.memberNameInput,
      );
      if (!result.ok) {
        this.memberModalError = result.reason ?? "INVALID_REQUEST";
        return;
      }
      this.lastMessage = this.t("ok");
      this.closeMemberModal();
      await this.refreshAll();
    } catch (e) {
      this.memberModalError = "apiError";
    } finally {
      this.loading = false;
    }
  }

  async updateMember(): Promise<void> {
    if (!this.memberIdInput || !this.memberNameInput) {
      this.lastMessage = this.t("INVALID_REQUEST");
      return;
    }
    await this.runAction(() =>
      this.api.updateMember(this.memberIdInput, this.memberNameInput),
    );
    this.clearMemberModal();
  }

  async deleteMember(id: string): Promise<void> {
    if (!id) {
      this.lastMessage = this.t("INVALID_REQUEST");
      return;
    }
    await this.runAction(() => this.api.deleteMember(id));
  }

  private async runAction(fn: () => Promise<ActionResult>): Promise<void> {
    this.loading = true;
    try {
      const result = await fn();
      this.lastMessage = this.describeResult(result);
      await this.refreshAll();
    } catch (e) {
      this.lastMessage = this.t("apiError");
    } finally {
      this.loading = false;
    }
  }

  private describeResult(result: ActionResult): string {
    if (result.ok) {
      if (typeof result.nextMemberId !== "undefined") {
        return result.nextMemberId
          ? `${this.t("ok")} -> ${result.nextMemberId}`
          : this.t("ok");
      }
      return this.t("ok");
    }
    const reasonKey = result.reason ?? "INVALID_REQUEST";
    return this.t(reasonKey);
  }

  get activeBook(): Book | undefined {
    return this.selectedBookId
      ? this.books.find((b) => b.id === this.selectedBookId)
      : undefined;
  }

  get activeMember(): Member | undefined {
    return this.selectedMemberId
      ? this.members.find((m) => m.id === this.selectedMemberId)
      : undefined;
  }

  get booksOnLoanCount(): number {
    return this.books.filter((b) => !!b.loanedTo).length;
  }

  get queuedCount(): number {
    return this.books.reduce(
      (acc, book) => acc + book.reservationQueue.length,
      0,
    );
  }

  bookStatusLabel(book: Book): string {
    if (book.loanedTo) {
      return `${this.t("loanedChip")}: ${book.loanedTo}`;
    }
    if (book.reservationQueue.length) {
      return `${this.t("queueChip")}: ${book.reservationQueue.length}`;
    }
    return this.t("availableChip");
  }

  formatDueDate(dueDate: string | null): string {
    if (!dueDate) return "";
    const date = new Date(dueDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    date.setHours(0, 0, 0, 0);
    const diffTime = date.getTime() - today.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    if (diffDays < 0) {
      return `⚠️ Overdue by ${Math.abs(diffDays)} day${Math.abs(diffDays) === 1 ? "" : "s"}`;
    } else if (diffDays === 0) {
      return "⚠️ Due today";
    } else if (diffDays <= 3) {
      return `⚠️ Due in ${diffDays} day${diffDays === 1 ? "" : "s"}`;
    } else {
      return `Due: ${date.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" })}`;
    }
  }

  canBorrow(): boolean {
    if (!this.activeBook || !this.selectedMemberId) return false;
    const book = this.activeBook;

    // Can borrow if book is available (not loaned and no queue)
    if (!book.loanedTo && book.reservationQueue.length === 0) return true;

    // Or if member is at head of reservation queue
    if (
      book.reservationQueue.length > 0 &&
      book.reservationQueue[0] === this.selectedMemberId
    ) {
      return true;
    }

    return false;
  }

  canReserve(): boolean {
    if (!this.activeBook || !this.selectedMemberId) return false;
    const book = this.activeBook;

    // Can't reserve if already borrowed by this member
    if (book.loanedTo === this.selectedMemberId) return false;

    // Can't reserve if already in reservation queue
    if (book.reservationQueue.includes(this.selectedMemberId)) return false;

    return true;
  }

  canCancelReservation(): boolean {
    if (!this.activeBook || !this.selectedMemberId) return false;
    return this.activeBook.reservationQueue.includes(this.selectedMemberId);
  }

  canReturn(): boolean {
    if (!this.activeBook || !this.selectedMemberId) return false;
    return this.activeBook.loanedTo === this.selectedMemberId;
  }

  canExtendLoan(): boolean {
    if (!this.activeBook || !this.selectedMemberId) return false;

    // Can only extend if you're the borrower
    if (this.activeBook.loanedTo !== this.selectedMemberId) return false;

    // Cannot extend if book has reservations (others are waiting)
    if (this.activeBook.reservationQueue.length > 0) return false;

    // Cannot extend if maximum extension reached
    return this.maxExtensionDays !== 0;
  }

  onBookSelectionChange(id: string | null) {
    this.selectedBookId = id;
    this.syncInputsWithSelection();
  }

  selectBook(id: string) {
    this.onBookSelectionChange(id);
  }

  onMemberSelectionChange(id: string | null) {
    this.selectedMemberId = id;
    this.syncInputsWithSelection();
  }

  selectMember(id: string) {
    this.onMemberSelectionChange(id);
  }

  private syncInputsWithSelection() {
    this.bookTitleInput = this.activeBook?.title ?? "";
    this.memberNameInput = this.activeMember?.name ?? "";
  }

  openBookModal(mode: "create" | "edit", book?: Book) {
    this.bookModalMode = mode;
    this.bookModalOpen = true;
    this.bookModalError = null;
    if (mode === "edit" && book) {
      this.bookIdInput = book.id;
      this.bookTitleInput = book.title;
    } else {
      this.bookIdInput = "";
      this.bookTitleInput = "";
    }
  }

  openMemberModal(mode: "create" | "edit", member?: Member) {
    this.memberModalMode = mode;
    this.memberModalOpen = true;
    this.memberModalError = null;
    if (mode === "edit" && member) {
      this.memberIdInput = member.id;
      this.memberNameInput = member.name;
    } else {
      this.memberIdInput = "";
      this.memberNameInput = "";
    }
  }

  closeBookModal() {
    this.bookModalOpen = false;
    this.clearBookModal();
  }

  closeMemberModal() {
    this.memberModalOpen = false;
    this.clearMemberModal();
  }

  async submitBookModal() {
    if (this.bookModalMode === "edit") {
      await this.updateBook();
    } else {
      await this.createBook();
    }
  }

  async submitMemberModal() {
    if (this.memberModalMode === "edit") {
      await this.updateMember();
    } else {
      await this.createMember();
    }
  }

  private clearBookModal() {
    this.bookIdInput = "";
    this.bookTitleInput = "";
    this.bookModalError = null;
  }

  private clearMemberModal() {
    this.memberIdInput = "";
    this.memberNameInput = "";
    this.memberModalError = null;
  }

  formatErrorMessage(errorCode: string): string {
    const messages: Record<string, string> = {
      BOOK_ALREADY_EXISTS:
        "A book with this ID already exists. Please use a different ID.",
      MEMBER_ALREADY_EXISTS:
        "A member with this ID already exists. Please use a different ID.",
      INVALID_REQUEST: "Please fill in all required fields.",
      apiError: "An error occurred while communicating with the API.",
    };
    return messages[errorCode] || `Error: ${errorCode}`;
  }

  dismissBookError() {
    this.bookModalError = null;
  }

  dismissMemberError() {
    this.memberModalError = null;
  }

  async loadOverdueBooks(): Promise<void> {
    this.loading = true;
    try {
      this.overdueBooks = await this.api.overdueBooks();
      this.lastMessage = this.t("ok");
    } catch (e) {
      this.lastMessage = this.t("apiError");
    } finally {
      this.loading = false;
    }
  }

  async loadMemberSummary(memberId: string): Promise<void> {
    if (!memberId) {
      this.memberSummary = null;
      return;
    }
    this.loading = true;
    try {
      this.memberSummary = await this.api.memberSummary(memberId);
      this.lastMessage = this.t("ok");
    } catch (e) {
      this.lastMessage = this.t("apiError");
      this.memberSummary = null;
    } finally {
      this.loading = false;
    }
  }

  calculateDaysOverdue(dueDate: string): number {
    const due = new Date(dueDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    due.setHours(0, 0, 0, 0);
    const diffTime = today.getTime() - due.getTime();
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return Math.max(0, diffDays);
  }
}
