package com.iticbanc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool.PoolInitializationException;

public class Main {
    // EJERCICIO 1: Filtrar statements por importe
    public static List<String> readStatementsByImport(Connection conn, double quantitat) throws SQLException {
        List<String> resultatQuery = new ArrayList<>();
        String query = "SELECT * FROM statements WHERE import >= ?";

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setDouble(1, quantitat);

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                String header = String.join(";", 
                    meta.getColumnName(1),
                    meta.getColumnName(3),
                    meta.getColumnName(8),
                    meta.getColumnName(10)
                );
                resultatQuery.add(header);

                while (rs.next()) {
                    String resultat = String.join(";",
                        rs.getString("id_statement"),
                        rs.getString("tipus_moviment"),
                        rs.getString("import"),
                        rs.getString("divisa")
                    );
                    resultatQuery.add(resultat);
                }
            }
        }
        return resultatQuery;
    }

    // EJERCICIO 2: Funciones para insertar cliente
    
    public static boolean existeCliente(Connection conn, String idFiscal) throws SQLException {
        String query = "SELECT COUNT(*) FROM clients WHERE id_fiscal = ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, idFiscal);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    public static void insertarCliente(Connection conn, List<String> datosCliente) throws SQLException {
        String nom = datosCliente.get(0);
        String cognoms = datosCliente.get(1);
        String idFiscal = datosCliente.get(2);

        if (existeCliente(conn, idFiscal)) {
            System.out.println("\n[AVÍS] El client amb ID fiscal " + idFiscal + " ja existeix a la base de dades.");
            return;
        }

        String insertQuery = "INSERT INTO clients (nom, cognoms, id_fiscal) VALUES (?, ?, ?)";
        boolean autocommitOriginal = conn.getAutoCommit();

        try {
            conn.setAutoCommit(false); 

            try (PreparedStatement ps = conn.prepareStatement(insertQuery)) {
                ps.setString(1, nom);
                ps.setString(2, cognoms);
                ps.setString(3, idFiscal);

                int filasAfectadas = ps.executeUpdate();

                if (filasAfectadas > 0) {
                    conn.commit();
                    System.out.println("\n[OK] Client insertat correctament!");
                } else {
                    conn.rollback();
                    System.out.println("\n[ERROR] No s'ha pogut insertar el client.");
                }
            }
        } catch (SQLException e) {
            conn.rollback();
            System.out.println("\n[ERROR] S'ha produït un error en la inserció. S'ha realitzat rollback.");
            throw e;
        } finally {
            conn.setAutoCommit(autocommitOriginal);
        }
    }

    public static void main(String[] args) {
        Properties p = new Properties();
        try {
            p.load(Files.newBufferedReader(Path.of("src", "main", "resources", "conn.properties")));
        } catch (IOException e) {
            System.err.println("No s'ha trobat el fitxer conn.properties a src/main/resources/");
            e.printStackTrace();
            return;
        }

        HikariConfig hkcfg = new HikariConfig();
        hkcfg.setJdbcUrl(p.getProperty("DB_URL"));
        hkcfg.setUsername(p.getProperty("DB_USER"));
        hkcfg.setPassword(p.getProperty("DB_PASSWORD"));
        hkcfg.setMaximumPoolSize(Integer.parseInt(p.getProperty("DB_MAX_POOL", "5")));

        try (HikariDataSource hkds = new HikariDataSource(hkcfg)) {
            try (Connection cnn = hkds.getConnection()) {
                System.out.println("Connectat!\n");

                // --- EXECUCIÓ EJERCICIO 1 ---
                double importMinim = 50.0;
                System.out.println("--- EJERCICIO 1: Statements amb import >= " + importMinim + " € ---");
                for (String linia : readStatementsByImport(cnn, importMinim)) {
                    System.out.println(linia);
                }

                // --- EXECUCIÓ EJERCICIO 2 ---
                System.out.println("\n--- EJERCICIO 2: Inserció de nou client ---");
                Scanner scanner = new Scanner(System.in);

                System.out.print("Introdueix el nom: ");
                String nom = scanner.nextLine();

                System.out.print("Introdueix els cognoms: ");
                String cognoms = scanner.nextLine();

                System.out.print("Introdueix l'ID fiscal (DNI/NIE): ");
                String idFiscal = scanner.nextLine();

                List<String> dadesClient = new ArrayList<>();
                dadesClient.add(nom);
                dadesClient.add(cognoms);
                dadesClient.add(idFiscal);

                insertarCliente(cnn, dadesClient);

            } catch (SQLException e) {
                System.out.println("No connectat!");
                e.printStackTrace();
            }
        } catch (PoolInitializationException e) {
            System.out.println("No s'ha pogut connectar a la base de dades: " + e.getMessage());
        }
    }
}