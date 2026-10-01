package com.knowledgegym.learning.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Xáo trộn Fisher–Yates — domain service thuần Java (không Spring, `DomainLayerArchTest` enforce).
 *
 * <p>Tồn tại thay vì `Collections.shuffle` để **random tường minh**: strategy quiz phải là hàm thuần
 * theo tham số của nó, còn `Collections.shuffle(list)` không-seed dùng `ThreadLocalRandom` ẩn nên
 * không test được và không tái lập được khi debug.
 */
public final class RandomOrder {

    private RandomOrder() {
    }

    /**
     * Bản sao đã xáo của {@code source}. Không sửa list đầu vào — nguồn thường là kết quả query
     * đang được dùng cho bước khác.
     */
    public static <T> List<T> shuffled(List<T> source, RandomGenerator random) {
        if (source == null || source.size() < 2) {
            return source == null ? List.of() : List.copyOf(source);
        }
        List<T> copy = new ArrayList<>(source);
        for (int i = copy.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = copy.get(i);
            copy.set(i, copy.get(j));
            copy.set(j, tmp);
        }
        return copy;
    }
}
