
// =============================================================
// University DB Java Client (JDBC) — PostgreSQL @ cs1.calstatela.edu
// Tasks:
//   1) Add / remove a project
//   2) Display student info + advisor name + major department
//   3) Display all projects that a faculty works on (PI and co-PI)
// =============================================================
import java.sql.*;
import java.util.Scanner;

public class App {
    // >>> IMPORTANT: update DB_NAME, USER, PASS before running <<<
    private static final String HOST = "cs1.calstatela.edu";
    private static final String PORT = "5432";
    private static final String DB_NAME = "<your_database_name>";
    private static final String USER = "<your_username>";
    private static final String PASS = "<your_password>";
    private static final String URL  = "jdbc:postgresql://" + HOST + ":" + PORT + "/" + DB_NAME;

    public static void main(String[] args) {
        try (Scanner in = new Scanner(System.in)) {
            // Load driver (optional on modern JVMs, but harmless)
            Class.forName("org.postgresql.Driver");

            try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
                // Ensure we use the right schema
                try (Statement s = conn.createStatement()) {
                    s.execute("SET search_path = university");
                }

                System.out.println("Connected to: " + URL);
                boolean quit = false;
                while (!quit) {
                    printMenu();
                    String choice = in.nextLine().trim();
                    switch (choice) {
                        case "1":
                            addProject(conn, in);
                            break;
                        case "2":
                            removeProject(conn, in);
                            break;
                        case "3":
                            showStudentInfo(conn, in);
                            break;
                        case "4":
                            showFacultyProjects(conn, in);
                            break;
                        case "5":
                            callFemaleFaculty(conn);
                            break;
                        case "6":
                            callTotalPeople(conn, in);
                            break;
                        case "q":
                        case "Q":
                            quit = true;
                            break;
                        default:
                            System.out.println("Unknown choice.");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printMenu() {
        System.out.println("\n=== University DB Menu ===");
        System.out.println("1) Add a project");
        System.out.println("2) Remove a project");
        System.out.println("3) Display student info + advisor + major dept");
        System.out.println("4) Display projects a faculty works on (PI + co-PI)");
        System.out.println("5) % female faculty");
        System.out.println("6) Total people on a project");
        System.out.println("Q) Quit");
        System.out.print("Choice: ");
    }

    // (1) Add
    private static void addProject(Connection conn, Scanner in) throws SQLException {
        System.out.print("Project No: ");
        String pno = in.nextLine().trim();
        System.out.print("Sponsor Name: ");
        String sponsor = in.nextLine().trim();
        System.out.print("Start Date (YYYY-MM-DD): ");
        String sd = in.nextLine().trim();
        System.out.print("End Date (YYYY-MM-DD): ");
        String ed = in.nextLine().trim();
        System.out.print("Budget (e.g., 250000): ");
        String budget = in.nextLine().trim();
        System.out.print("PI SSN (9 chars): ");
        String pi = in.nextLine().trim();

        String sql = "INSERT INTO Project(project_no, sponsor_name, start_date, end_date, budget, pi_ssn) "
                   + "VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pno);
            ps.setString(2, sponsor);
            ps.setDate(3, Date.valueOf(sd));
            ps.setDate(4, Date.valueOf(ed));
            ps.setBigDecimal(5, new java.math.BigDecimal(budget));
            ps.setString(6, pi);
            ps.executeUpdate();
            System.out.println("Project added.");
        }
    }

    // (1) Remove
    private static void removeProject(Connection conn, Scanner in) throws SQLException {
        System.out.print("Project No to remove: ");
        String pno = in.nextLine().trim();
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM Project WHERE project_no = ?")) {
            ps.setString(1, pno);
            int n = ps.executeUpdate();
            if (n > 0) System.out.println("Project removed.");
            else System.out.println("No such project.");
        }
    }

    // (2) Student info + advisor + major dept
    private static void showStudentInfo(Connection conn, Scanner in) throws SQLException {
        System.out.print("Student SSN: ");
        String ssn = in.nextLine().trim();
        String sql = ""
            + "SELECT s.ssn, s.name, s.age, s.gender, s.degree_program,\n"
            + "       d.dept_name AS major_dept,\n"
            + "       adv.name AS advisor_name\n"
            + "FROM GradStudent s\n"
            + "JOIN Department d ON d.dept_no = s.major_dept_no\n"
            + "LEFT JOIN Student_Advising sa ON sa.student_ssn = s.ssn\n"
            + "LEFT JOIN GradStudent adv ON adv.ssn = sa.advisor_ssn\n"
            + "WHERE s.ssn = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ssn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.printf("Student: %s (%s)%n", rs.getString("name"), rs.getString("ssn"));
                    System.out.printf(" Age: %d, Gender: %s, Program: %s%n",
                        rs.getInt("age"), rs.getString("gender"), rs.getString("degree_program"));
                    System.out.printf(" Major Dept: %s%n", rs.getString("major_dept"));
                    String adv = rs.getString("advisor_name");
                    System.out.printf(" Advisor: %s%n", (adv == null ? "(none)" : adv));
                } else {
                    System.out.println("No such student.");
                }
            }
        }
    }

    // (3) All projects a faculty works on (as PI or co-PI)
    private static void showFacultyProjects(Connection conn, Scanner in) throws SQLException {
        System.out.print("Professor SSN: ");
        String ssn = in.nextLine().trim();
        String sql = ""
            + "SELECT 'PI' AS role, p.project_no, p.sponsor_name, p.start_date, p.end_date, p.budget\n"
            + "  FROM Project p\n"
            + " WHERE p.pi_ssn = ?\n"
            + "UNION ALL\n"
            + "SELECT 'Co-PI' AS role, p.project_no, p.sponsor_name, p.start_date, p.end_date, p.budget\n"
            + "  FROM Faculty_Project fp\n"
            + "  JOIN Project p ON p.project_no = fp.project_no\n"
            + " WHERE fp.prof_ssn = ?\n"
            + " ORDER BY role, project_no";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ssn);
            ps.setString(2, ssn);
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("[%s] %s — %s (%s to %s) $%s%n",
                            rs.getString("role"),
                            rs.getString("project_no"),
                            rs.getString("sponsor_name"),
                            rs.getDate("start_date"),
                            rs.getDate("end_date"),
                            rs.getBigDecimal("budget").toPlainString());
                }
                if (!any) System.out.println("No projects for that professor.");
            }
        }
    }

    // (extra) female_faculty()
    private static void callFemaleFaculty(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT female_faculty() AS pct")) {
            if (rs.next()) {
                System.out.printf("Female faculty: %.2f%%%n", rs.getDouble("pct"));
            }
        }
    }

    // (extra) total_people(pno)
    private static void callTotalPeople(Connection conn, Scanner in) throws SQLException {
        System.out.print("Project No: ");
        String pno = in.nextLine().trim();
        try (PreparedStatement ps = conn.prepareStatement("SELECT total_people(?) AS total")) {
            ps.setString(1, pno);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.printf("Total people on %s: %d%n", pno, rs.getInt("total"));
                }
            }
        } catch (SQLException ex) {
            System.out.println("Error: " + ex.getMessage());
        }
    }
}
