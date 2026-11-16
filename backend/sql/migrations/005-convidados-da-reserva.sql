-- Lista de convidados da reserva, para a portaria liberar a entrada.
-- JSON com os nomes (["Ana Souza", "Bruno Lima"]); null quando o morador não informou ninguém.

ALTER TABLE reservations
    ADD COLUMN guests TEXT NULL AFTER resident_name;
