# Oracle SQL Migration Scanner

A parser-based Java/XML SQL scanner for Oracle migration analysis.

This tool scans Java and XML codebases, reconstructs embedded SQL, parses the SQL with a real SQL parser, identifies Oracle-specific migration risks, and exports the results as CSV, JSON, and HTML reports.

## What It Does

The scanner is built to answer a practical migration question:

"What SQL do we have, which tables and columns does it touch, and how hard will it be to move away from Oracle-specific behavior?"

It focuses on three areas that are usually weak in quick-and-dirty scanners:

- Java parsing: uses a Java AST instead of line-by-line regex matching
- SQL parsing: uses JSqlParser instead of regex-based table extraction
- XML parsing: uses structured and secure XML parsing

## Why This Version Is Better

Compared to regex-based scanners, this version is more reliable for:

- multi-line SQL strings
- Java text blocks
- string concatenation chains
- basic `StringBuilder` and `StringBuffer` flows
- `String.format(...)`
- annotation-based SQL
- constants resolved from fields and local variables
- structured SQL parsing for tables, columns, and joins
- Oracle migration risk scoring

## Architecture

```text
Codebase
   |
   v
File Walker
   |
   +--> JavaExtractor (JavaParser AST)
   |        |
   |        v
   |    SQL reconstruction
   |
   +--> XmlExtractor (secure DOM parsing)
            |
            v
        SQL normalization
            |
            v
        SqlAnalyzer (JSqlParser)
            |
            +--> table extraction
            +--> column extraction
            +--> join extraction
            +--> Oracle risk detection
            +--> migration difficulty scoring
            |
            v
         Deduplicated results
            |
            +--> CSV
            +--> JSON
            +--> HTML dashboard
```

## Project Layout

```text
src/main/java/com/migration/scanner
|-- OracleQueryScannerApp.java
|-- config
|   `-- Config.java
|-- extractor
|   |-- ExpressionResolver.java
|   |-- ExtractionAccumulator.java
|   |-- JavaExtractor.java
|   |-- JavaScope.java
|   `-- XmlExtractor.java
|-- model
|   |-- Result.java
|   |-- ScanReport.java
|   |-- SqlAnalysis.java
|   `-- Summary.java
|-- report
|   |-- Exporter.java
|   `-- SummaryPrinter.java
|-- scanner
|   |-- FileWalker.java
|   `-- ScannerEngine.java
`-- sql
    |-- SqlAnalyzer.java
    |-- SqlAstWalker.java
    |-- SqlDetector.java
    `-- SqlNormalizer.java
```

## Technology Stack

- Java 11
- Maven
- [JavaParser 3.25.10](https://github.com/javaparser/javaparser)
- [JSqlParser 4.9](https://github.com/JSQLParser/JSqlParser)

## Build

From the project root:

```powershell
mvn -q -DskipTests package
```

This creates a runnable shaded jar:

```text
target/oracle-query-scanner-1.0.0.jar
```

## Run

### Recommended: run the shaded jar

```powershell
java -jar target\oracle-query-scanner-1.0.0.jar --input "C:\path\to\scan" --output "C:\path\to\reports"
```

Short flags also work:

```powershell
java -jar target\oracle-query-scanner-1.0.0.jar -i "C:\path\to\scan" -o "C:\path\to\reports"
```

### Example

```powershell
java -jar target\oracle-query-scanner-1.0.0.jar --input "C:\Users\vinay kumar\Desktop\my-app" --output "C:\Users\vinay kumar\Desktop\scan-output"
```

### Maven exec

You can also run it through Maven:

```powershell
mvn compile exec:java '-Dexec.args=--input C:\path\to\scan --output C:\path\to\reports'
```

On PowerShell, the whole `-Dexec.args=...` value should be wrapped in single quotes.

## CLI Options

### Named arguments

- `--input <path>` or `-i <path>`: folder to scan
- `--output <path>` or `-o <path>`: output folder for reports
- `--help` or `-h`: print usage

### Positional arguments

Backward-compatible positional arguments still work:

```powershell
java -jar target\oracle-query-scanner-1.0.0.jar "C:\path\to\scan" "C:\path\to\reports"
```

### Defaults

If you do not provide arguments:

- input defaults to the current directory (`.`)
- output defaults to `reports`

## Supported Inputs

The scanner currently walks the target folder and processes:

- `.java`
- `.xml`

## Java SQL Extraction Capabilities

The Java extractor uses JavaParser AST traversal and can currently reconstruct SQL from:

- string literals
- text blocks
- binary string concatenation with `+`
- local variables and field constants
- `String.format(...)`
- basic `StringBuilder` and `StringBuffer` usage
- method-call sinks such as:
  - `prepareStatement`
  - `createQuery`
  - `createNativeQuery`
  - `executeQuery`
  - `addBatch`
- annotation values

## XML SQL Extraction Capabilities

The XML extractor uses secure DOM parsing and looks for SQL in common places such as:

- `<select>`
- `<insert>`
- `<update>`
- `<delete>`
- `<sql>`
- `<query>`
- `<statement>`
- SQL-like attributes such as `sql`, `query`, `value`, and `statement`
- CDATA sections

Security hardening includes:

- secure processing enabled
- DOCTYPE disabled
- external entities disabled
- external parameter entities disabled

## SQL Analysis

Once SQL is reconstructed, the scanner parses it with JSqlParser and extracts:

- statement type
- tables
- columns
- joins
- parse status
- Oracle migration risks
- migration difficulty score

## Oracle Risk Detection

The scanner currently flags common Oracle migration concerns including:

- `NVL`
- `DECODE`
- `ROWNUM`
- old Oracle outer join syntax `(+ )` / `(+)`
- `CONNECT BY`
- `START WITH`
- `MINUS`
- `SYSDATE`
- `SYSTIMESTAMP`
- `TO_DATE`
- `TO_CHAR`
- `LISTAGG`
- `MERGE`

## Difficulty Scoring

The tool turns risk detection into a rough migration difficulty score:

- `LOW`
- `MEDIUM`
- `HIGH`

Current scoring is additive and based on detected Oracle-specific constructs. Higher scores indicate more migration complexity.

## Output

Each run generates three report formats in the output directory:

### 1. CSV

Designed for spreadsheet review and bulk filtering.

Columns include:

- package
- class
- file path
- line number
- source type
- statement type
- SQL parsed status
- risk score
- difficulty
- risks
- tables
- columns
- joins
- query

### 2. JSON

Designed for machine consumption and downstream tooling.

The JSON file includes:

- run summary
- per-query results
- parse status
- extracted metadata

### 3. HTML

Designed for human review.

The dashboard includes:

- total query count
- parsed query count
- difficulty breakdown
- top tables
- top risks
- tabular query listing

## Example Output Shape

```json
{
  "summary": {
    "totalQueries": 1200,
    "parsedQueries": 1083,
    "byType": {
      "SELECT": 800,
      "UPDATE": 210
    }
  },
  "results": [
    {
      "package": "com.example.repo",
      "class": "UserDao",
      "path": "C:\\repo\\src\\main\\java\\com\\example\\repo\\UserDao.java",
      "line": 42,
      "sourceType": "JAVA",
      "statementType": "SELECT",
      "sqlParsed": true,
      "riskScore": 2,
      "difficulty": "LOW",
      "risks": ["NVL"],
      "tables": ["USERS"],
      "columns": ["USERS.ID", "USERS.NAME"],
      "joins": ["LEFT"],
      "parseError": "",
      "query": "select nvl(name, 'NA') from users"
    }
  ]
}
```

## Practical Usage Tips

- Prefer scanning the application source root, not the entire disk
- Keep the output folder outside the scanned folder when possible
- Avoid scanning `target/`, generated folders, and report folders
- Start with a smaller repo slice if you are evaluating performance
- Use the JSON output if you want to feed the results into another tool

## Current Limitations

This is a strong upgrade over regex scanning, but it is not a full whole-program dataflow engine.

Current limitations include:

- no deep inter-procedural SQL reconstruction
- limited support for helper methods that return SQL indirectly
- basic `StringBuilder` reconstruction, not full control-flow simulation
- dynamic SQL assembled from runtime-only values may remain partial
- XML support is generic and may need tuning for framework-specific mapper styles
- SQL parsing depends on JSqlParser compatibility with the input dialect

## Good Next Improvements

Useful next steps for this project would be:

- framework-aware XML extraction for MyBatis, iBatis, and Hibernate mappings
- better inter-method Java string resolution
- explicit exclusion rules for folders like `target`, `.git`, and report output folders
- richer Oracle compatibility rules and remediation hints
- unit tests for Java reconstruction and SQL analysis
- configurable risk scoring
- markdown or Excel export

## Development

Build:

```powershell
mvn -q -DskipTests compile
```

Package:

```powershell
mvn -q -DskipTests package
```

Run locally:

```powershell
java -jar target\oracle-query-scanner-1.0.0.jar --input src\main\java --output reports
```

## Git Ignore

The repository ignores:

- `target/`
- generated report folders
- IDE files
- common OS metadata files

See [.gitignore](./.gitignore).

## License

Add your preferred license here.
