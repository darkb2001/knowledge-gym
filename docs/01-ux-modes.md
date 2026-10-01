# UX Modes — 10 hình thức ôn tập

## Tổng quan

| # | Mode | Loại | Engagement | Phase |
|---|------|------|-----------|-------|
| 1 | Flashcard + SRS | Memorization | Daily habit | 2 |
| 2 | Quiz (trắc nghiệm) | Knowledge check | Quick test | 2 |
| 3 | Mock Interview | Application | Deep practice | 2 |
| 4 | Code Challenge | Hands-on | Skill building | 2 |
| 5 | Knowledge Mindmap | Visual learning | Big picture | 4 |
| 6 | Comparison Table | Analytical | Concept clarity | 2 |
| 7 | Diagram Playground | Interactive | Architecture understanding | 3 |
| 8 | Daily Challenge | Gamification | Habit formation | 4 |
| 9 | Weakness Radar | Analytics | Targeted improvement | 2 |
| 10 | Progress Heatmap | Motivation | Consistency tracking | 2 |

---

## 1. Flashcard + SRS (Spaced Repetition System)

```
┌─────────────────────────────────────────────┐
│  1. Phân biệt JDK, JRE và JVM?    [Q1]     │
│                                             │
│         Nhấn để lật ▶                      │
└─────────────────────────────────────────────┘

        ↓ Lật ↓

┌─────────────────────────────────────────────┐
│  JVM = engine thực thi bytecode             │
│  JRE = JVM + thư viện runtime               │
│  JDK = JRE + công cụ dev                    │
│                                             │
│  [Again]  [Hard]  [Good]  [Easy]           │
│   1d      1d      1d      4d               │
└─────────────────────────────────────────────┘
```

> Nhãn dưới nút phản ánh lịch m5 (SM-2 thang 0–3, đơn vị **ngày**): lần ôn đầu Again/Hard/Good → 1 ngày,
> Easy → 4 ngày. Không phải 1m/6m như mock cũ.

- **SM-2 Algorithm**: ease_factor, interval, repetitions — card khó xuất hiện thường xuyên hơn
- **Deck per module**: "Java Core Flashcards", "Database Flashcards"
- **Custom deck**: User tự tạo deck từ các câu đã bookmark

---

## 2. Quiz Mode (Trắc nghiệm tự sinh)

```
┌─────────────────────────────────────────────┐
│  Quiz: Java Core — 10 câu / 15 phút        │
│  ████████░░ 5/10                    ⏱ 8:32  │
│                                             │
│  Q5: HashMap sử dụng cấu trúc gì?          │
│                                             │
│  ○ Array + LinkedList (chaining)            │
│  ● Array + BST (từ Java 8)                 │
│  ○ TreeMap                                 │
│  ○ SkipList                                │
│                                             │
│  [Next →]   [Bookmark ⭐]                  │
└─────────────────────────────────────────────┘
```

- Hệ thống tự tạo distractors (đáp án sai) từ content
- Match theo difficulty: Junior / Mid / Senior
- Timer + scoring + review sau khi nộp

---

## 3. Mock Interview (Mô phỏng phỏng vấn)

```
┌─────────────────────────────────────────────┐
│  Mock Interview: Spring Boot                │
│  ████████░░ 5/10                    ⏱ 12:00 │
│                                             │
│  Q3: Giải thích @Transactional hoạt động    │
│      như thế nào?                           │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ Viết câu trả lời của bạn...         │    │
│  │                                     │    │
│  │ @Transactional sử dụng proxy AOP    │    │
│  │ để wrap method trong transaction...  │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  [Submit]   [Skip →]   [Show Hint 💡]      │
└─────────────────────────────────────────────┘

        ↓ Submit ↓

┌─────────────────────────────────────────────┐
│  Score: 78/100                              │
│                                             │
│  ✅ Đúng: Proxy AOP, rollback khi exception │
│  ⚠️ Thiếu: Propagation, readOnly, isolation │
│  ❌ Sai: "Controller nên dùng" → phải Service│
│                                             │
│  [Xem answer mẫu]  [Luyện lại]  [Next →]  │
└─────────────────────────────────────────────┘
```

- **2 chế độ chấm**: Rule-based (keyword matching) hoặc AI (GPT/Claude API)
- **Speech mode**: Ghi âm nói → speech-to-text → chấm (thực tế hơn cho onsite interview)

---

## 4. Code Challenge

```
┌─────────────────────────────────────────────┐
│  Code: Viết custom HashMap (đơn giản)       │
│  Difficulty: ★★★☆☆                          │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ public class MyHashMap<K,V> {       │    │
│  │   // Viết code ở đây               │    │
│  │ }                                   │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  Test cases: 0/4 passed                     │
│  [Run Tests]  [Hint]  [View Solution]       │
└─────────────────────────────────────────────┘
```

- Integrated Monaco Editor (VS Code trong browser)
- Test cases tự động verify
- Gợi ý progressive: hint 1 → hint 2 → solution

---

## 5. Knowledge Mindmap (Interactive)

```
                    ┌─────────────┐
                    │  Java Core  │
                    └──────┬──────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
     ┌──────────┐   ┌──────────┐   ┌──────────┐
     │  Memory  │   │Collection│   │  OOP     │
     └────┬─────┘   └────┬─────┘   └──────────┘
     ┌────┼────┐    ┌────┼────┐
     ▼    ▼    ▼    ▼    ▼    ▼
   Stack Heap GC  Map  Set  List
    [2]  [3] [6]  [7]  [8]  [9]  ← số câu hỏi
```

- Click node → mở câu hỏi / quiz cho topic đó
- Color theo mastery: đỏ (yếu) → vàng → xanh (thạo)
- D3.js hoặc Cytoscape.js cho rendering

---

## 6. Comparison Table Builder

```
┌─────────────────────────────────────────────┐
│  Compare: == vs .equals() vs hashCode       │
│                                             │
│  ┌─────────┬──────────┬──────────┬────────┐ │
│  │ Aspect  │ ==       │ .equals()│hashCode│ │
│  ├─────────┼──────────┼──────────┼────────┤ │
│  │ Type    │ reference│ value    │ bucket │ │
│  │ Null    │ true     │ false*   │ error  │ │
│  │ Override│ No       │ Yes      │ Yes    │ │
│  └─────────┴──────────┴──────────┴────────┘ │
│                                             │
│  Tự sinh từ content trong docs               │
└─────────────────────────────────────────────┘
```

---

## 7. Diagram Playground

```
┌─────────────────────────────────────────────┐
│  @Transactional Flow                        │
│                                             │
│  Controller → [AOP Proxy] → Service         │
│                    │                        │
│                    ▼                        │
│             beginTransaction()               │
│                    │                        │
│                    ▼                        │
│             Repository.save()                │
│                    │                        │
│              ┌─────┴─────┐                  │
│              ▼           ▼                  │
│         Success?     Exception?             │
│              │           │                  │
│              ▼           ▼                  │
│          commit()    rollback()             │
│                                             │
│  [Chạy animation ▶]  [Edit source]         │
└─────────────────────────────────────────────┘
```

- Mermaid.js → interactive SVG
- Click vào step → giải thích chi tiết
- Animation chạy flow

---

## 8. Daily Streak + Challenge

```
┌─────────────────────────────────────────────┐
│  🔥 Streak: 12 ngày liên tiếp              │
│                                             │
│  Mon Tue Wed Thu Fri Sat Sun                │
│   ✅  ✅  ✅  ✅   🔵   ○   ○              │
│                                             │
│  Daily Challenge hôm nay:                   │
│  "Explain difference between HashMap and    │
│   ConcurrentHashMap" (2 min)                │
│                                             │
│  [Bắt đầu]  [Skip hôm nay]                 │
└─────────────────────────────────────────────┘
```

---

## 9. Weakness Radar

```
┌─────────────────────────────────────────────┐
│  Knowledge Radar                            │
│                                             │
│          Java Core                          │
│             ★★★★☆                          │
│            ╱      ╲                         │
│    DSA ★★☆      Spring ★★★★☆              │
│          │      │                           │
│    Micro ★★★   Database ★★★☆☆             │
│            ╲      ╱                         │
│         Design ★★★★                         │
│                                             │
│  ⚠️ Cần ôn: DSA, Multithreading            │
│  [Ôn ngay →]                                │
└─────────────────────────────────────────────┘
```

---

## 10. Progress Heatmap (GitHub-style)

```
┌─────────────────────────────────────────────┐
│  Hoạt động học tập (12 tháng)              │
│  ▫️▫️▪️▪️▫️▫️▫️  Week 1                    │
│  ▫️▪️▪️▪️▫️▫️▫️  Week 2                    │
│  ▫️▫️▪️▪️▪️▫️▫️  Week 3                    │
│  ...                                        │
│  ▪️ = ít  ▪️▪️ = vừa  ▪️▪️▪️ = nhiều       │
└─────────────────────────────────────────────┘
```
