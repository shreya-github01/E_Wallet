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

import com.google.gson.JsonObject;
import com.payflow.db.DatabaseConnection;

public class ProfileServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // --- GET: Fetch user data to show in the modal ---
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        JsonObject jsonResponse = new JsonObject();

        if (session == null || session.getAttribute("user_id") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Not logged in.");
            out.print(jsonResponse.toString());
            out.flush();
            return;
        }

        int userId = (int) session.getAttribute("user_id");
        Connection conn = DatabaseConnection.getConnection();

        // Updated SQL to include mobile and address
        String sql = "SELECT name, email, mobile, address FROM users WHERE user_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                jsonResponse.addProperty("success", true);
                jsonResponse.addProperty("name", rs.getString("name"));
                jsonResponse.addProperty("email", rs.getString("email"));
                jsonResponse.addProperty("mobile", rs.getString("mobile"));
                jsonResponse.addProperty("address", rs.getString("address")); // Add address
            } else {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "User not found.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Database error.");
        }

        out.print(jsonResponse.toString());
        out.flush();
    }

    // --- POST: Update user data in the database ---
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        JsonObject jsonResponse = new JsonObject();

        if (session == null || session.getAttribute("user_id") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Not logged in.");
            out.print(jsonResponse.toString());
            out.flush();
            return;
        }

        int userId = (int) session.getAttribute("user_id");
        String name = request.getParameter("name");
        String mobile = request.getParameter("mobile");
        String address = request.getParameter("address"); // Get new address

        Connection conn = DatabaseConnection.getConnection();
        // Updated SQL to include mobile and address
        String sql = "UPDATE users SET name = ?, mobile = ?, address = ? WHERE user_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, mobile);
            stmt.setString(3, address); // Add address
            stmt.setInt(4, userId);

            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated > 0) {
                // IMPORTANT: Update the name in the session too!
                session.setAttribute("name", name);

                jsonResponse.addProperty("success", true);
                jsonResponse.addProperty("message", "Profile updated successfully!");
            } else {
                jsonResponse.addProperty("success", false);
                jsonResponse.addProperty("message", "Failed to update profile.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            jsonResponse.addProperty("success", false);
            jsonResponse.addProperty("message", "Database error.");
        }

        out.print(jsonResponse.toString());
        out.flush();
    }
}