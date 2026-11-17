
package com.wedid.backend.estimate.dto;

public record GuestRange(
        Type type,
        Integer minGuests,
        Integer maxGuests
) {
    public enum Type {
        RANGE,
        UNDER_100,
        OVER_300,
        UNDECIDED
    }

    public static GuestRange fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException(
                    "하객 수 선택은 필수입니다."
            );
        }

        return switch (code) {
            case "UNDER_100" ->
                    new GuestRange(Type.UNDER_100, 1, 99);

            case "FROM_100_TO_150" ->
                    new GuestRange(Type.RANGE, 100, 150);

            case "FROM_150_TO_200" ->
                    new GuestRange(Type.RANGE, 150, 200);

            case "FROM_200_TO_250" ->
                    new GuestRange(Type.RANGE, 200, 250);

            case "FROM_250_TO_300" ->
                    new GuestRange(Type.RANGE, 250, 300);


            case "OVER_300" ->
                    new GuestRange(Type.OVER_300, 300, 300);

            case "UNDECIDED" ->
                    new GuestRange(Type.UNDECIDED, 150, 200);


            default -> throw new IllegalArgumentException(
                    "지원하지 않는 하객 수 선택입니다: " + code
            );
        };
    }
}
