"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.activate = activate;
const vscode = require("vscode");
const cp = require("child_process");
const path = require("path");
const fs = require("fs");
let outputChannel;
let symbolDatabase = null;
let workspaceRoot = '';
let semanticTokensProvider;
// Define your semantic token legend
const tokenTypes = [
    'class', 'function', 'variable', 'parameter', 'property',
    'enumMember', 'event', 'method', 'macro', 'type'
];
const tokenModifiers = [
    'readonly', 'static', 'deprecated'
];
const legend = new vscode.SemanticTokensLegend(tokenTypes, tokenModifiers);
// Add this function to test colors
function logThemeColors() {
    const config = vscode.workspace.getConfiguration();
    console.log('Current theme:', config.get('workbench.colorTheme'));
    // You can also check if semantic highlighting is enabled
    console.log('Semantic highlighting enabled:', config.get('editor.semanticHighlighting.enabled'));
}
function activate(context) {
    console.log('Extension "fpl" is now active!');
    logThemeColors();
    // Create output channel for compiler messages
    outputChannel = vscode.window.createOutputChannel('FPL Compiler');
    context.subscriptions.push(outputChannel);
    const diagnostics = vscode.languages.createDiagnosticCollection('fpl');
    context.subscriptions.push(diagnostics);
    // Watch for symbol database changes
    if (vscode.workspace.workspaceFolders && vscode.workspace.workspaceFolders.length > 0) {
        workspaceRoot = vscode.workspace.workspaceFolders[0].uri.fsPath;
        // Load initially
        loadSymbolDatabase(workspaceRoot);
        // Watch for changes
        const symbolFilePattern = new vscode.RelativePattern(workspaceRoot, 'symbol-map.json');
        const watcher = vscode.workspace.createFileSystemWatcher(symbolFilePattern);
        watcher.onDidCreate(() => loadSymbolDatabase(workspaceRoot));
        watcher.onDidChange(() => loadSymbolDatabase(workspaceRoot));
        context.subscriptions.push(watcher);
    }
    // Register hover provider
    const hoverProvider = vscode.languages.registerHoverProvider('fpl', {
        provideHover(document, position) {
            return provideHoverInfo(document, position);
        }
    });
    context.subscriptions.push(hoverProvider);
    // Register definition provider
    const definitionProvider = vscode.languages.registerDefinitionProvider('fpl', {
        provideDefinition(document, position) {
            return provideDefinition(document, position);
        }
    });
    context.subscriptions.push(definitionProvider);
    // Register semantic tokens provider
    semanticTokensProvider = new FplSemanticTokensProvider();
    const semanticTokensProviderDisposable = vscode.languages.registerDocumentSemanticTokensProvider('fpl', semanticTokensProvider, legend);
    context.subscriptions.push(semanticTokensProviderDisposable);
    vscode.workspace.onDidSaveTextDocument(document => {
        if (document.languageId !== 'fpl')
            return;
        const workspaceFolder = vscode.workspace.getWorkspaceFolder(document.uri);
        const workingDir = workspaceFolder ? workspaceFolder.uri.fsPath : path.dirname(document.uri.fsPath);
        workspaceRoot = workingDir;
        // Compile and generate symbol database
        compileAndGenerateSymbols(workingDir, document, diagnostics);
    });
    const onDidChangeTextDocument = vscode.workspace.onDidChangeTextDocument(event => {
        if (event.document.languageId === 'fpl' && semanticTokensProvider) {
            semanticTokensProvider.handleDocumentChange(event.document, event.contentChanges);
        }
    });
    context.subscriptions.push(onDidChangeTextDocument);
    vscode.workspace.onDidSaveTextDocument(document => {
        if (document.languageId !== 'fpl')
            return;
        const workspaceFolder = vscode.workspace.getWorkspaceFolder(document.uri);
        const workingDir = workspaceFolder ? workspaceFolder.uri.fsPath : path.dirname(document.uri.fsPath);
        workspaceRoot = workingDir;
        // Compile and generate symbol database
        compileAndGenerateSymbols(workingDir, document, diagnostics);
    });
}
function compileAndGenerateSymbols(workingDir, document, diagnostics) {
    // Clear previous output and show the channel
    outputChannel.clear();
    outputChannel.appendLine(`Compiling ${path.basename(document.uri.fsPath)}...`);
    // Run the compiler and parse the output
    const filePath = document.uri.fsPath;
    var compilerOutput = "";
    const execOptions = {
        encoding: 'utf8',
        cwd: workingDir
    };
    try {
        compilerOutput = cp.execSync(`fplcomp`, execOptions).toString();
        loadSymbolDatabase(workingDir);
    }
    catch (error) {
        compilerOutput = error.stdout.toString() + error.stderr.toString();
        outputChannel.appendLine('Compilation failed:');
    }
    // Always show the full compiler output
    if (compilerOutput.trim()) {
        outputChannel.appendLine('\n--- Compiler Output ---');
        outputChannel.appendLine(compilerOutput);
    }
    diagnostics.clear();
    const errorMap = new Map();
    const linkerErrors = [];
    const lines = compilerOutput.split(/\r?\n/);
    for (const line of lines) {
        const match = line.match(/^(.*?):(\d+).(\d+)-(\d+).(\d+):\s+(.*)$/);
        if (match) {
            // File-specific error
            const [, file, lineStr, colStr, lineEndStr, colEndStr, message] = match;
            const lineNum = parseInt(lineStr) - 1;
            const colNum = parseInt(colStr) - 1;
            const endLineNum = parseInt(lineEndStr) - 1;
            const endColNum = parseInt(colEndStr);
            const range = new vscode.Range(lineNum, colNum, endLineNum, endColNum);
            const diagnostic = new vscode.Diagnostic(range, message, vscode.DiagnosticSeverity.Error);
            // Convert relative path to absolute path
            const absoluteFilePath = path.isAbsolute(file) ? file : path.resolve(workingDir, file);
            const fileUri = vscode.Uri.file(absoluteFilePath);
            const existing = errorMap.get(absoluteFilePath) ?? [];
            existing.push(diagnostic);
            errorMap.set(absoluteFilePath, existing);
        }
        else if (line.trim() && line.startsWith('Error')) {
            // Potential linker error or other general error
            linkerErrors.push(line.trim());
        }
    }
    // Handle linker errors by creating diagnostics on the current file
    if (linkerErrors.length > 0) {
        const linkerDiagnostics = linkerErrors.map(error => new vscode.Diagnostic(new vscode.Range(0, 0, 0, 0), // Top of file
        `Linker Error: ${error}`, vscode.DiagnosticSeverity.Error));
        const currentFileErrors = errorMap.get(filePath) ?? [];
        currentFileErrors.push(...linkerDiagnostics);
        errorMap.set(filePath, currentFileErrors);
    }
    // Final pass: set diagnostics per file
    diagnostics.clear();
    for (const [file, diags] of errorMap.entries()) {
        const fileUri = vscode.Uri.file(file);
        diagnostics.set(fileUri, diags);
    }
    // Show output channel if there are errors
    if (errorMap.size > 0 || linkerErrors.length > 0) {
        outputChannel.show(true); // true = preserve focus on editor
    }
}
function loadSymbolDatabase(workingDir) {
    const symbolFile = path.join(workingDir, 'symbol-map.json');
    if (fs.existsSync(symbolFile)) {
        try {
            const content = fs.readFileSync(symbolFile, 'utf8');
            symbolDatabase = JSON.parse(content);
            console.log(`Loaded ${symbolDatabase.symbols.length} symbols`);
            semanticTokensProvider.refresh(); // Trigger re-coloring
        }
        catch (error) {
            console.error('Failed to load symbol database:', error);
            symbolDatabase = null;
        }
    }
}
function provideHoverInfo(document, position) {
    if (!symbolDatabase)
        return undefined;
    const wordRange = document.getWordRangeAtPosition(position);
    if (!wordRange)
        return undefined;
    const word = document.getText(wordRange);
    const currentFile = path.relative(workspaceRoot, document.uri.fsPath).replace(/\\/g, '/');
    const currentLine = position.line + 1; // VS Code is 0-based, your data is 1-based
    // Find symbol at current position
    const symbol = symbolDatabase.symbols.find((s) => {
        if (s.name !== word)
            return false;
        // Check if we're at the definition
        if (s.definition.filename === currentFile && s.definition.line === currentLine) {
            return true;
        }
        // Check if we're at a reference (filter out empty references)
        return s.references.some(ref => ref.filename === currentFile && ref.line === currentLine);
    });
    if (symbol) {
        const hoverText = [
            `**${symbol.name}** (${symbol.kind})`,
            `Type: \`${symbol.type}\``,
            `${symbol.mutable ? 'mutable' : 'imutable'}`,
        ];
        // Add definition location if we're at a reference
        if (symbol.definition.filename !== currentFile || symbol.definition.line !== currentLine) {
            hoverText.push(`Defined in: ${symbol.definition.filename}:${symbol.definition.line}`);
        }
        return new vscode.Hover(hoverText);
    }
}
// Go-to-definition provider
function provideDefinition(document, position) {
    if (!symbolDatabase)
        return undefined;
    const wordRange = document.getWordRangeAtPosition(position);
    if (!wordRange)
        return undefined;
    const word = document.getText(wordRange);
    const currentFile = path.relative(workspaceRoot, document.uri.fsPath).replace(/\\/g, '/');
    const symbol = symbolDatabase.symbols.find((s) => {
        if (s.name !== word)
            return false;
        // Make sure this symbol is referenced at current location
        return s.references.some(ref => ref.filename === currentFile && ref.line === position.line + 1) || (s.definition.filename === currentFile && s.definition.line === position.line + 1);
    });
    if (symbol && symbol.definition.filename) {
        const defPath = path.resolve(workspaceRoot, symbol.definition.filename);
        return new vscode.Location(vscode.Uri.file(defPath), new vscode.Position(symbol.definition.line - 1, symbol.definition.column - 1));
    }
}
class FplSemanticTokensProvider {
    constructor() {
        this._onDidChangeSemanticTokens = new vscode.EventEmitter();
        this.onDidChangeSemanticTokens = this._onDidChangeSemanticTokens.event;
        this._documentTokens = new Map();
        this._documentVersions = new Map();
    }
    refresh() {
        // Clear all cached tokens to force regeneration
        this._documentTokens.clear();
        this._documentVersions.clear();
        this._onDidChangeSemanticTokens.fire();
    }
    handleDocumentChange(document, changes) {
        const uri = document.uri.toString();
        // Get the cached tokens for this document
        const cachedTokens = this._documentTokens.get(uri);
        if (!cachedTokens) {
            return;
        }
        // Apply the changes to the cached tokens
        const updatedTokens = this.applyChangesToCachedTokens(cachedTokens, changes);
        if (updatedTokens) {
            this._documentTokens.set(uri, updatedTokens);
            this._documentVersions.set(uri, document.version);
        }
        else {
            // Changes were too complex, remove cache to force regeneration
            this._documentTokens.delete(uri);
            this._documentVersions.delete(uri);
        }
        // Fire the change event
        this._onDidChangeSemanticTokens.fire();
    }
    applyChangesToCachedTokens(cachedTokens, changes) {
        const data = Array.from(cachedTokens.data);
        const tokens = [];
        // Decode tokens
        let currentLine = 0;
        let currentChar = 0;
        for (let i = 0; i < data.length; i += 5) {
            const deltaLine = data[i];
            const deltaChar = data[i + 1];
            const length = data[i + 2];
            const tokenType = data[i + 3];
            const tokenModifiers = data[i + 4];
            currentLine += deltaLine;
            currentChar = deltaLine === 0 ? currentChar + deltaChar : deltaChar;
            tokens.push({
                line: currentLine,
                char: currentChar,
                length: length,
                tokenType: tokenType,
                tokenModifiers: tokenModifiers
            });
        }
        // Apply each change
        for (const change of changes) {
            const startLine = change.range.start.line;
            const startChar = change.range.start.character;
            const endLine = change.range.end.line;
            const endChar = change.range.end.character;
            const newText = change.text;
            const linesAdded = newText.split('\n').length - 1;
            const linesRemoved = endLine - startLine;
            const lineDelta = linesAdded - linesRemoved;
            // Calculate character delta for same-line changes
            let charDelta = 0;
            if (lineDelta === 0 && startLine === endLine) {
                // Single line change
                charDelta = newText.length - (endChar - startChar);
            }
            // console.log(`Change: lines ${startLine}-${endLine}, chars ${startChar}-${endChar}, lineDelta: ${lineDelta}, charDelta: ${charDelta}`);
            // Update token positions
            for (let i = tokens.length - 1; i >= 0; i--) {
                const token = tokens[i];
                if (lineDelta !== 0) {
                    // Handle vertical changes (line insertions/deletions)
                    if (token.line > endLine) {
                        // Token is after the change - adjust line number
                        token.line += lineDelta;
                        // console.log(`Moved token vertically from line ${token.line - lineDelta} to ${token.line}`);
                    }
                    else if (token.line >= startLine && token.line <= endLine) {
                        // Token is in the changed range - remove it
                        // console.log(`Removed token in changed line range at line ${token.line}`);
                        tokens.splice(i, 1);
                    }
                }
                else if (lineDelta === 0 && charDelta !== 0) {
                    // Handle horizontal changes (same line edits)
                    if (token.line === startLine) {
                        if (token.char >= endChar) {
                            // Token starts after the change - adjust character position
                            token.char += charDelta;
                            // console.log(`Moved token horizontally from char ${token.char - charDelta} to ${token.char}`);
                        }
                        else if (token.char + token.length > startChar) {
                            // Token overlaps with change - remove it
                            // console.log(`Removed overlapping token at line ${token.line}, char ${token.char}`);
                            tokens.splice(i, 1);
                        }
                    }
                }
                else if (lineDelta === 0 && charDelta === 0) {
                    // No net change (e.g., replace text with same length text)
                    if (token.line >= startLine && token.line <= endLine) {
                        if (token.line === startLine && (token.char >= startChar && token.char < endChar)) {
                            // Token is within the replaced range - remove it
                            // console.log(`Removed token in replaced range at line ${token.line}, char ${token.char}`);
                            tokens.splice(i, 1);
                        }
                    }
                }
            }
        }
        // Re-encode tokens
        const tokensBuilder = new vscode.SemanticTokensBuilder(legend);
        // Sort tokens by line, then by character
        tokens.sort((a, b) => {
            if (a.line !== b.line)
                return a.line - b.line;
            return a.char - b.char;
        });
        for (const token of tokens) {
            tokensBuilder.push(token.line, token.char, token.length, token.tokenType, token.tokenModifiers);
        }
        return tokensBuilder.build();
    }
    provideDocumentSemanticTokens(document) {
        if (!symbolDatabase)
            return undefined;
        const uri = document.uri.toString();
        const currentFile = path.relative(workspaceRoot, document.uri.fsPath).replace(/\\/g, '/');
        // Check if we have cached tokens that are still valid
        const cachedTokens = this._documentTokens.get(uri);
        const cachedVersion = this._documentVersions.get(uri);
        if (cachedTokens && cachedVersion === document.version) {
            return cachedTokens;
        }
        // Generate fresh tokens
        const tokensBuilder = new vscode.SemanticTokensBuilder(legend);
        // Process each symbol
        for (const symbol of symbolDatabase.symbols) {
            const tokenType = this.mapSymbolKindToTokenType(symbol.kind);
            if (tokenType === -1)
                continue;
            const tokenModifiers = this.getTokenModifiers(symbol);
            // Add definition token
            if (symbol.definition.filename === currentFile && symbol.definition.line > 0) {
                tokensBuilder.push(symbol.definition.line - 1, symbol.definition.column - 1, symbol.name.length, tokenType, tokenModifiers);
            }
            // Add reference tokens
            for (const ref of symbol.references) {
                if (ref.filename === currentFile && ref.line > 0) {
                    tokensBuilder.push(ref.line - 1, ref.column - 1, symbol.name.length, tokenType, tokenModifiers);
                }
            }
        }
        const tokens = tokensBuilder.build();
        // Cache the tokens
        this._documentTokens.set(uri, tokens);
        this._documentVersions.set(uri, document.version);
        return tokens;
    }
    mapSymbolKindToTokenType(kind) {
        const mapping = {
            'type': 'class',
            'class': 'class',
            'enum': 'enum',
            'function': 'function', // Functions
            'constant': 'enumMember', // Constants
            'global': 'namespace', // Global variables
            'field': 'property', // Class fields
            'var': 'variable', // Local variables
            'inline field': 'property' // Inline fields
        };
        const mappedType = mapping[kind];
        return mappedType ? tokenTypes.indexOf(mappedType) : -1;
    }
    getTokenModifiers(symbol) {
        let modifiers = 0;
        // Add modifiers based on symbol properties
        if (symbol.kind === 'constant') {
            modifiers |= (1 << tokenModifiers.indexOf('readonly'));
        }
        if (symbol.kind === 'global') {
            modifiers |= (1 << tokenModifiers.indexOf('static'));
        }
        if (symbol.mutable === false) {
            modifiers |= (1 << tokenModifiers.indexOf('readonly'));
        }
        return modifiers;
    }
    encodeTokens(tokens) {
        const tokensBuilder = new vscode.SemanticTokensBuilder(legend);
        for (const token of tokens) {
            tokensBuilder.push(token.line, token.startChar, token.length, token.tokenType, token.tokenModifiers);
        }
        return tokensBuilder.build();
    }
}
//# sourceMappingURL=extension.js.map