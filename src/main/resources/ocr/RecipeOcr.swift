// Reads text off an image or a PDF using the on-device Vision engine - the same
// one behind Live Text. Invoked by the app when a page is uploaded to be scanned.
//
// Kept as source rather than a checked-in binary: it is platform specific, and
// the app compiles it on first use and caches the result.
import AppKit
import Foundation
import Vision

func cgImages(at path: String) -> [CGImage] {
    let url = URL(fileURLWithPath: path)

    // A PDF is rendered a page at a time, at a scale Vision reads comfortably.
    if path.lowercased().hasSuffix(".pdf"),
       let document = CGPDFDocument(url as CFURL) {
        var pages: [CGImage] = []
        for number in 1...max(document.numberOfPages, 1) {
            guard let page = document.page(at: number) else { continue }
            let box = page.getBoxRect(.mediaBox)
            let scale: CGFloat = 2.0
            let width = Int(box.width * scale), height = Int(box.height * scale)
            guard width > 0, height > 0,
                  let context = CGContext(
                      data: nil, width: width, height: height,
                      bitsPerComponent: 8, bytesPerRow: 0,
                      space: CGColorSpaceCreateDeviceRGB(),
                      bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)
            else { continue }
            context.setFillColor(CGColor(gray: 1, alpha: 1))
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
            context.scaleBy(x: scale, y: scale)
            context.drawPDFPage(page)
            if let image = context.makeImage() { pages.append(image) }
        }
        return pages
    }

    guard let image = NSImage(contentsOf: url),
          let data = image.tiffRepresentation,
          let bitmap = NSBitmapImageRep(data: data),
          let cg = bitmap.cgImage else { return [] }
    return [cg]
}

guard CommandLine.arguments.count > 1 else {
    FileHandle.standardError.write("usage: RecipeOcr <image-or-pdf>\n".data(using: .utf8)!)
    exit(64)
}

let pages = cgImages(at: CommandLine.arguments[1])
guard !pages.isEmpty else {
    FileHandle.standardError.write("could not read that file as an image or PDF\n".data(using: .utf8)!)
    exit(65)
}

for page in pages {
    let request = VNRecognizeTextRequest()
    request.recognitionLevel = .accurate
    request.usesLanguageCorrection = true
    try VNImageRequestHandler(cgImage: page, options: [:]).perform([request])
    for observation in request.results ?? [] {
        if let line = observation.topCandidates(1).first { print(line.string) }
    }
}
