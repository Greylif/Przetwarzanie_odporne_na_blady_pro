package com.example.pro_spring.model;


/**
 * Reprezentuje odpowiedz typu PROMISE w protokole Paxos.
 *
 * @param promised informacja, czy serwer zlozyl obietnice
 * @param acceptedProposal identyfikator wczesniej zaakceptowanej propozycji lub -1, jesli brak
 * @param acceptedValue wartosc zaakceptowana wraz z acceptedProposal lub -1, jesli brak
 *
 */
public record Promise(
    boolean promised,
    int acceptedProposal,
    int acceptedValue
) {

}
