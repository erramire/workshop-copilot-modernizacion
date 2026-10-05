package org.sample.azure.student.application;

public record StudentRegisteredEvent(Integer id, String name, String email) {

    // Sin datos personales si el evento acaba en un log (ADR-009)
    @Override
    public String toString() {
        return "StudentRegisteredEvent[id=" + id + "]";
    }
}
