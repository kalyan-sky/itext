(() => {
    const MAX_BYTES = 20 * 1024 * 1024;

    const form = document.getElementById("upload-form");
    const input = document.getElementById("file-input");
    const dropzone = document.getElementById("dropzone");
    const formatsHint = document.getElementById("formats-hint");
    const selected = document.getElementById("selected");
    const selectedName = document.getElementById("selected-name");
    const selectedSize = document.getElementById("selected-size");
    const clearButton = document.getElementById("clear-button");
    const errorBox = document.getElementById("error");
    const convertButton = document.getElementById("convert-button");
    const buttonLabel = convertButton.querySelector(".button-label");
    const uploadCard = document.getElementById("upload-card");
    const result = document.getElementById("result");
    const resultName = document.getElementById("result-name");
    const resultSize = document.getElementById("result-size");
    const resultProducer = document.getElementById("result-producer");
    const downloadLink = document.getElementById("download-link");
    const openLink = document.getElementById("open-link");
    const againButton = document.getElementById("again-button");
    const preview = document.getElementById("preview");

    let file = null;
    let extensions = [];
    let pdfUrl = null;

    const formatSize = (bytes) => {
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
    };

    const extensionOf = (name) => {
        const dot = name.lastIndexOf(".");
        return dot < 0 ? "" : name.slice(dot + 1).toLowerCase();
    };

    const showError = (message) => {
        errorBox.textContent = message;
        errorBox.hidden = !message;
    };

    const selectFile = (candidate) => {
        showError("");
        if (!candidate) return;

        const ext = extensionOf(candidate.name);
        if (extensions.length && !extensions.includes(ext)) {
            showError(`.${ext || "?"} files are not supported. Use one of: ${extensions.join(", ")}`);
            return;
        }
        if (candidate.size > MAX_BYTES) {
            showError(`The file is ${formatSize(candidate.size)}; the limit is 20 MB.`);
            return;
        }

        file = candidate;
        selectedName.textContent = file.name;
        selectedSize.textContent = formatSize(file.size);
        selected.hidden = false;
        convertButton.disabled = false;
    };

    const clearFile = () => {
        file = null;
        input.value = "";
        selected.hidden = true;
        convertButton.disabled = true;
        showError("");
    };

    const setBusy = (busy) => {
        convertButton.classList.toggle("busy", busy);
        convertButton.disabled = busy || !file;
        buttonLabel.textContent = busy ? "Converting…" : "Convert to PDF";
    };

    const readError = async (response) => {
        try {
            const body = await response.json();
            if (body.error) return body.error;
        } catch {
            // Not JSON; fall through to the status text
        }
        return `Conversion failed (${response.status} ${response.statusText})`;
    };

    const showResult = (blob, sourceName, producer) => {
        if (pdfUrl) URL.revokeObjectURL(pdfUrl);
        pdfUrl = URL.createObjectURL(blob);

        const dot = sourceName.lastIndexOf(".");
        const pdfName = `${dot > 0 ? sourceName.slice(0, dot) : sourceName}.pdf`;

        resultName.textContent = pdfName;
        resultSize.textContent = formatSize(blob.size);
        resultProducer.textContent = producer ? `Producer: ${producer}` : "";
        resultProducer.hidden = !producer;
        downloadLink.href = pdfUrl;
        downloadLink.download = pdfName;
        openLink.href = pdfUrl;
        preview.src = pdfUrl;

        uploadCard.hidden = true;
        result.hidden = false;
        result.scrollIntoView({ behavior: "smooth", block: "start" });
    };

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (!file) return;

        showError("");
        setBusy(true);
        const body = new FormData();
        body.append("file", file);

        try {
            const response = await fetch("api/convert", { method: "POST", body });
            if (!response.ok) {
                showError(await readError(response));
                return;
            }
            showResult(await response.blob(), file.name, response.headers.get("X-PDF-Producer"));
        } catch {
            showError("Could not reach the server. Check that it is running and try again.");
        } finally {
            setBusy(false);
        }
    });

    input.addEventListener("change", () => selectFile(input.files[0]));
    clearButton.addEventListener("click", clearFile);

    againButton.addEventListener("click", () => {
        result.hidden = true;
        uploadCard.hidden = false;
        preview.removeAttribute("src");
        clearFile();
    });

    ["dragenter", "dragover"].forEach((type) =>
        dropzone.addEventListener(type, (event) => {
            event.preventDefault();
            dropzone.classList.add("dragging");
        }));
    ["dragleave", "drop"].forEach((type) =>
        dropzone.addEventListener(type, (event) => {
            event.preventDefault();
            dropzone.classList.remove("dragging");
        }));
    dropzone.addEventListener("drop", (event) => selectFile(event.dataTransfer.files[0]));

    // Dropping a file outside the zone would otherwise navigate away from the page
    window.addEventListener("dragover", (event) => event.preventDefault());
    window.addEventListener("drop", (event) => event.preventDefault());

    fetch("api/formats")
        .then((response) => (response.ok ? response.json() : null))
        .then((body) => {
            if (!body) return;
            extensions = body.extensions;
            input.accept = extensions.map((ext) => `.${ext}`).join(",");
            formatsHint.textContent = `${extensions.map((ext) => `.${ext}`).join("  ")} · up to 20 MB`;
        })
        .catch(() => {
            // The server validates the format anyway
        });
})();
