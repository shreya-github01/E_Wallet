package com.payflow.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.google.gson.JsonObject;
import com.payflow.db.DatabaseConnection;

public class AddFundsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        JsonObject jsonResponse = new JsonObject();

        if (session == null || session.getAttribute("account_id") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Not logged in.");
            out.print(jsonResponse.toString());
            out.flush();
            return;
        }

        int accountId = (int) session.getAttribute("account_id");
        double amount = Double.parseDouble(request.getParameter("amount"));
        String paymentMethod = request.getParameter("paymentMethod");

        Connection conn = DatabaseConnection.getConnection();

        try {
            // Start transaction
            conn.setAutoCommit(false);

            // 1. Add funds to the user's account
            String updateSql = "UPDATE accounts SET balance = balance + ? WHERE account_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setDouble(1, amount);
                stmt.setInt(2, accountId);
                stmt.executeUpdate();
            }

            // 2. Create a transaction record for this
            String insertTxSql = "INSERT INTO transactions (receiver_account_id, amount, transaction_type, note) " +
                    "VALUES (?, ?, 'add_funds', ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertTxSql)) {
                stmt.setInt(1, accountId);
                stmt.setDouble(2, amount);
                stmt.setString(3, "Added via " + paymentMethod);
                stmt.executeUpdate();
            }

            // Commit the transaction
            conn.commit();
            jsonResponse.addProperty("success", true);
            jsonResponse.addProperty("message", "Funds added successfully!");

        } catch (SQLException e) {
            try {
                conn.rollback(); // Rollback on error
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "A database error occurred.");
        } finally {
            try {
                conn.setAutoCommit(true); // Reset to default
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        out.print(jsonResponse.toString());
        out.flush();
    }
}