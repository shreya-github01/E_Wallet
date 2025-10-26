package com.payflow.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import com.payflow.db.DatabaseConnection;
import com.google.gson.JsonObject;

public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String email = request.getParameter("email");
        String password = request.getParameter("password");

        Connection conn = DatabaseConnection.getConnection();
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        JsonObject jsonResponse = new JsonObject();

        String sql = "SELECT user_id, name FROM users WHERE email = ? AND password = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            stmt.setString(2, password); // In production, check a hashed password

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                // --- SUCCESSFUL LOGIN ---
                int userId = rs.getInt("user_id");
                String name = rs.getString("name");

                // 1. Create a new session
                HttpSession session = request.getSession();

                // 2. Store user data in the session
                session.setAttribute("user_id", userId);
                session.setAttribute("name", name);
                session.setAttribute("email", email); // <-- THIS LINE IS ADDED

                // 3. Send success response
                jsonResponse.addProperty("success", true);
                jsonResponse.addProperty("message", "Login successful!");

            } else {
                // --- FAILED LOGIN ---
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Invalid email or password.");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Database error occurred.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        out.print(jsonResponse.toString());
        out.flush();
    }
}