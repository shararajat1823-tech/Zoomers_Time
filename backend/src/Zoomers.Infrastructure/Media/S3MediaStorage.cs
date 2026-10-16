using System.Net;
using Amazon.S3;
using Amazon.S3.Model;
using Microsoft.Extensions.Options;
using Zoomers.Application.Abstractions;

namespace Zoomers.Infrastructure.Media;

/// <summary>
/// Object storage backed by any S3-compatible provider (used in production with
/// Cloudflare R2's free tier). The client uploads directly to a presigned PUT
/// URL and feeds return presigned GET URLs, so media bytes never pass through
/// the API host. Reads/writes go through the AWS SDK, which also serves the
/// authorized-download path when a presigned URL isn't used.
/// </summary>
public sealed class S3MediaStorage : IMediaStorage
{
    private readonly IAmazonS3 _s3;
    private readonly S3StorageOptions _options;

    public S3MediaStorage(IAmazonS3 s3, IOptions<S3StorageOptions> options)
    {
        _s3 = s3;
        _options = options.Value;
    }

    public async Task<SignedUrl> CreateUploadUrlAsync(string storageKey, string contentType, long maxBytes, CancellationToken ct = default)
    {
        var expires = DateTime.UtcNow.AddMinutes(_options.UploadUrlMinutes);
        var url = await _s3.GetPreSignedURLAsync(new GetPreSignedUrlRequest
        {
            BucketName = _options.Bucket,
            Key = storageKey,
            Verb = HttpVerb.PUT,
            Expires = expires,
            ContentType = contentType,
        });
        return new SignedUrl(url, expires);
    }

    public async Task<SignedUrl> CreateDownloadUrlAsync(string storageKey, CancellationToken ct = default)
    {
        var expires = DateTime.UtcNow.AddMinutes(_options.DownloadUrlMinutes);
        var url = await _s3.GetPreSignedURLAsync(new GetPreSignedUrlRequest
        {
            BucketName = _options.Bucket,
            Key = storageKey,
            Verb = HttpVerb.GET,
            Expires = expires,
        });
        return new SignedUrl(url, expires);
    }

    public async Task<long?> TryGetObjectSizeAsync(string storageKey, CancellationToken ct = default)
    {
        try
        {
            var meta = await _s3.GetObjectMetadataAsync(new GetObjectMetadataRequest
            {
                BucketName = _options.Bucket,
                Key = storageKey,
            }, ct);
            return meta.ContentLength;
        }
        catch (AmazonS3Exception e) when (e.StatusCode == HttpStatusCode.NotFound)
        {
            return null;
        }
    }

    public async Task SaveAsync(string storageKey, Stream content, string contentType, CancellationToken ct = default)
    {
        await _s3.PutObjectAsync(new PutObjectRequest
        {
            BucketName = _options.Bucket,
            Key = storageKey,
            InputStream = content,
            ContentType = contentType,
            AutoCloseStream = false,
            DisablePayloadSigning = true, // R2 does not support streaming (chunked) SigV4 payloads
        }, ct);
    }

    public async Task<Stream?> OpenReadAsync(string storageKey, CancellationToken ct = default)
    {
        try
        {
            var response = await _s3.GetObjectAsync(new GetObjectRequest
            {
                BucketName = _options.Bucket,
                Key = storageKey,
            }, ct);
            return response.ResponseStream;
        }
        catch (AmazonS3Exception e) when (e.StatusCode == HttpStatusCode.NotFound)
        {
            return null;
        }
    }

    public async Task DeleteAsync(string storageKey, CancellationToken ct = default)
    {
        await _s3.DeleteObjectAsync(new DeleteObjectRequest
        {
            BucketName = _options.Bucket,
            Key = storageKey,
        }, ct);
    }
}
