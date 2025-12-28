export interface Book {
  id: string;
  title: string;
  loanedTo: string | null;
  dueDate: string | null;
  firstDueDate: string | null;
  reservationQueue: string[];
}

export interface Member {
  id: string;
  name: string;
}

export interface ActionResult {
  ok: boolean;
  reason?: string;
  nextMemberId?: string | null;
}

export interface OverdueBook {
  id: string;
  title: string;
  loanedTo: string;
  dueDate: string;
  firstDueDate: string | null;
  reservationQueue: string[];
}

export interface LoanSummary {
  bookId: string;
  title: string;
  dueDate: string;
}

export interface ReservationSummary {
  bookId: string;
  title: string;
  position: number;
}

export interface MemberSummary {
  memberId: string;
  memberName: string;
  loans: LoanSummary[];
  reservations: ReservationSummary[];
}

export class LibraryApiService {
  constructor(private readonly baseUrl = "http://localhost:8080/api") {}

  async books(): Promise<Book[]> {
    const res = await fetch(`${this.baseUrl}/books`);
    const data = await res.json();
    return data.items as Book[];
  }

  async members(): Promise<Member[]> {
    const res = await fetch(`${this.baseUrl}/members`);
    const data = await res.json();
    return data.items as Member[];
  }

  async overdueBooks(): Promise<OverdueBook[]> {
    const res = await fetch(`${this.baseUrl}/overdue`);
    const data = await res.json();
    return data.items as OverdueBook[];
  }

  async memberSummary(memberId: string): Promise<MemberSummary> {
    const res = await fetch(`${this.baseUrl}/members/${memberId}/summary`);
    return res.json();
  }

  async borrow(bookId: string, memberId: string): Promise<ActionResult> {
    return this.post("/borrow", { bookId, memberId });
  }

  async reserve(bookId: string, memberId: string): Promise<ActionResult> {
    return this.post("/reserve", { bookId, memberId });
  }

  async cancelReservation(
    bookId: string,
    memberId: string,
  ): Promise<ActionResult> {
    return this.post("/cancel-reservation", { bookId, memberId });
  }

  async returnBook(bookId: string, memberId: string): Promise<ActionResult> {
    return this.post("/return", { bookId, memberId });
  }

  async extendLoan(
    bookId: string,
    memberId: string,
    days: number,
  ): Promise<ActionResult> {
    return this.post("/extend", { bookId, memberId, days: days.toString() });
  }

  async createBook(id: string, title: string): Promise<ActionResult> {
    return this.post("/books", { id, title });
  }

  async updateBook(id: string, title: string): Promise<ActionResult> {
    return this.put("/books", { id, title });
  }

  async deleteBook(id: string): Promise<ActionResult> {
    return this.delete("/books", { id });
  }

  async createMember(id: string, name: string): Promise<ActionResult> {
    return this.post("/members", { id, name });
  }

  async updateMember(id: string, name: string): Promise<ActionResult> {
    return this.put("/members", { id, name });
  }

  async deleteMember(id: string): Promise<ActionResult> {
    return this.delete("/members", { id });
  }

  private async post(
    path: string,
    payload: Record<string, string>,
  ): Promise<ActionResult> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return res.json();
  }

  private async put(
    path: string,
    payload: Record<string, string>,
  ): Promise<ActionResult> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return res.json();
  }

  private async delete(
    path: string,
    payload: Record<string, string>,
  ): Promise<ActionResult> {
    const res = await fetch(`${this.baseUrl}${path}`, {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return res.json();
  }
}
