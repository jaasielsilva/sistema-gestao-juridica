package br.com.jaasielsilva.gestaojuridica.processo;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Pagina de resultados (a primeira pagina e a 0). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> map) {
        return new PageResponse<>(page.getContent().stream().map(map).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
